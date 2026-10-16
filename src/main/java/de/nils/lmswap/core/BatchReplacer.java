package de.nils.lmswap.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.ILitematicaBlockStatePalette;
import fi.dy.masa.litematica.schematic.container.LitematicaBitArray;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import de.nils.lmswap.core.ReplacementResult.Message;
import de.nils.lmswap.core.ResolvedScope.RegionPart;
import de.nils.lmswap.data.RuleSet.ChainMode;

/**
 * Batch replacement of several materials (part D).
 * <p>
 * <b>Analysis</b> (preview) without touching the schematic: one scan per region counts the blocks per
 * (palette state, scope signature), where the signature is a bit mask of the rules whose scope contains the position
 * (constant per region if no rule uses world boxes). From this small table the exact numbers follow for both chain
 * modes: hits per rule, blocks shadowed by earlier rules, and the material delta.
 * <p>
 * <b>Execution</b>: {@link ChainMode#ORIGINAL} (default) runs all rules in one {@link SchematicReplacer} pass, the first
 * matching rule wins and every position is replaced at most once. {@link ChainMode#SEQUENTIAL} runs one pass per rule
 * on the current state (re-planned per pass, so a rule sees the results of the previous ones). Either way the result is
 * one combined undo record.
 */
public final class BatchReplacer
{
    /** Scope signatures are a long bit mask */
    public static final int MAX_RULES = 63;

    private BatchReplacer()
    {
    }

    /**
     * @param hits          blocks changed per rule
     * @param shadowed      blocks the rule would match in the original schematic but an earlier rule takes (only
     *                      {@link ChainMode#ORIGINAL}; overlapping filters)
     * @param ruleErrors    per rule the first validation error, null if valid
     * @param chains        pairs (i, j): the target of rule i is the source material of rule j
     * @param materialDelta item -> change of the required amount (material list delta)
     */
    public record Analysis(int[] hits, int[] shadowed, List<Message> ruleErrors, List<int[]> chains,
                           Map<Item, Integer> materialDelta)
    {
        public int totalHits()
        {
            int sum = 0;

            for (int h : this.hits)
            {
                sum += h;
            }

            return sum;
        }

        public boolean hasErrors()
        {
            return this.ruleErrors.stream().anyMatch(e -> e != null);
        }
    }

    // ---- validation ----

    /**
     * @return per rule the first validation error or null
     */
    public static List<Message> validate(LitematicaSchematic schematic, List<BatchRule> rules, MaterialResolver resolver)
    {
        List<Message> errors = new ArrayList<>();

        for (BatchRule rule : rules)
        {
            errors.add(validate(schematic, rule, resolver));
        }

        return errors;
    }

    @Nullable
    private static Message validate(LitematicaSchematic schematic, BatchRule rule, MaterialResolver resolver)
    {
        List<Message> overrideErrors = ReplacementRule.validateOverrides(rule.target(), rule.overrides());

        if (overrideErrors.isEmpty() == false)
        {
            return overrideErrors.getFirst();
        }

        ItemReplacementPlan plan = ItemReplacementPlan.forItem(schematic, rule.source(), resolver);

        if (plan.isEmpty())
        {
            return null; // nothing to replace (now) - not an error, the rule just has no hits
        }

        Message conditionError = plan.validateConditions(rule.conditions());

        if (conditionError != null)
        {
            return conditionError;
        }

        if (plan.getSourceBlocks().contains(rule.target()) == false && plan.isAllowedTarget(rule.target(), rule.keepProperties()) == false)
        {
            return Message.of("lmswap.error.properties_differ", BlockStateMapper.blockId(plan.getSourceBlocks().iterator().next()),
                              BlockStateMapper.blockId(rule.target()));
        }

        return null;
    }

    // ---- analysis ----

    public static Analysis analyze(LitematicaSchematic schematic, @Nullable SchematicPlacement placement, List<BatchRule> rules,
                                   Scope batchScope, ChainMode mode, MaterialResolver resolver)
    {
        if (rules.size() > MAX_RULES)
        {
            throw new IllegalArgumentException("At most " + MAX_RULES + " rules per batch");
        }

        final int n = rules.size();
        int[] hits = new int[n];
        int[] shadowed = new int[n];
        List<Message> errors = new ArrayList<>(validate(schematic, rules, resolver));
        Map<Item, Integer> delta = new LinkedHashMap<>();
        ResolvedScope[] scopes = new ResolvedScope[n];

        for (int i = 0; i < n; ++i)
        {
            scopes[i] = ScopeResolver.resolve(rules.get(i).effectiveScope(batchScope), schematic, placement);

            if (scopes[i] == null && errors.get(i) == null)
            {
                errors.set(i, Message.of("lmswap.error.scope_unresolvable"));
            }
        }

        // Rules with errors do not take part in the simulation
        boolean[] active = new boolean[n];

        for (int i = 0; i < n; ++i)
        {
            active[i] = errors.get(i) == null && scopes[i] != null;
        }

        Map<BlockState, Item> itemCache = new HashMap<>();

        for (String regionName : schematic.getAreas().keySet())
        {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);

            if (container == null)
            {
                continue;
            }

            RegionPart[] parts = new RegionPart[n];

            for (int i = 0; i < n; ++i)
            {
                parts[i] = active[i] ? scopes[i].forRegion(regionName) : null;
            }

            Map<SignatureKey, Integer> table = countSignatures(container, rules, parts, resolver);

            for (Map.Entry<SignatureKey, Integer> e : table.entrySet())
            {
                simulate(e.getKey().state(), e.getKey().mask(), e.getValue(), rules, mode, resolver, hits, shadowed, delta);
            }
        }

        delta.values().removeIf(v -> v == 0);

        return new Analysis(hits, shadowed, errors, findChains(rules, resolver, itemCache), delta);
    }

    private record SignatureKey(BlockState state, long mask)
    {
    }

    /**
     * Counts the blocks per (original state, mask of rules whose scope contains the position). Only states that some
     * rule matches are relevant (later states in sequential mode are always produced from those).
     */
    private static Map<SignatureKey, Integer> countSignatures(LitematicaBlockStateContainer container, List<BatchRule> rules,
                                                              RegionPart[] parts, MaterialResolver resolver)
    {
        ILitematicaBlockStatePalette palette = container.getPalette();
        final int paletteSize = palette.getPaletteSize();
        BlockState[] states = new BlockState[paletteSize];
        boolean[] relevant = new boolean[paletteSize];
        boolean anyRelevant = false;
        long wholeMask = 0;
        List<Integer> boxRules = new ArrayList<>();

        for (int i = 0; i < parts.length; ++i)
        {
            if (parts[i] != null)
            {
                if (parts[i].isWhole())
                {
                    wholeMask |= 1L << i;
                }
                else
                {
                    boxRules.add(i);
                }
            }
        }

        for (int id = 0; id < paletteSize; ++id)
        {
            BlockState state = palette.getBlockState(id);
            states[id] = state;

            if (state != null)
            {
                for (int i = 0; i < parts.length; ++i)
                {
                    if (parts[i] != null && rules.get(i).matches(state, resolver))
                    {
                        relevant[id] = true;
                        anyRelevant = true;
                        break;
                    }
                }
            }
        }

        Map<SignatureKey, Integer> table = new HashMap<>();

        if (anyRelevant == false)
        {
            return table;
        }

        LitematicaBitArray storage = container.getArray();
        Vec3i size = container.getSize();
        long index = 0;

        if (boxRules.isEmpty())
        {
            int[] perId = new int[paletteSize];
            final long volume = (long) size.getX() * size.getY() * size.getZ();

            for (; index < volume; ++index)
            {
                int id = storage.getAt(index);

                if (id < paletteSize && relevant[id])
                {
                    ++perId[id];
                }
            }

            for (int id = 0; id < paletteSize; ++id)
            {
                if (perId[id] > 0)
                {
                    table.merge(new SignatureKey(states[id], wholeMask), perId[id], Integer::sum);
                }
            }

            return table;
        }

        for (int y = 0; y < size.getY(); ++y)
        {
            for (int z = 0; z < size.getZ(); ++z)
            {
                for (int x = 0; x < size.getX(); ++x, ++index)
                {
                    int id = storage.getAt(index);

                    if (id < paletteSize && relevant[id])
                    {
                        long mask = wholeMask;

                        for (int i : boxRules)
                        {
                            if (parts[i].contains(x, y, z))
                            {
                                mask |= 1L << i;
                            }
                        }

                        table.merge(new SignatureKey(states[id], mask), 1, Integer::sum);
                    }
                }
            }
        }

        return table;
    }

    private static void simulate(BlockState original, long mask, int count, List<BatchRule> rules, ChainMode mode,
                                 MaterialResolver resolver, int[] hits, int[] shadowed, Map<Item, Integer> delta)
    {
        BlockState current = original;
        boolean taken = false;

        for (int i = 0; i < rules.size(); ++i)
        {
            if ((mask & (1L << i)) == 0)
            {
                continue;
            }

            BatchRule rule = rules.get(i);

            if (mode == ChainMode.ORIGINAL)
            {
                if (rule.matches(original, resolver))
                {
                    if (taken)
                    {
                        shadowed[i] += count;
                    }
                    else
                    {
                        taken = true;
                        BlockState mapped = rule.map(original);

                        if (mapped != original)
                        {
                            hits[i] += count;
                            current = mapped;
                        }
                    }
                }
            }
            else if (rule.matches(current, resolver))
            {
                BlockState mapped = rule.map(current);

                if (mapped != current)
                {
                    hits[i] += count;
                    current = mapped;
                }
            }
        }

        if (current != original)
        {
            for (MaterialResolver.ItemAmount amount : resolver.materials(original))
            {
                delta.merge(amount.item(), -count * amount.count(), Integer::sum);
            }

            for (MaterialResolver.ItemAmount amount : resolver.materials(current))
            {
                delta.merge(amount.item(), count * amount.count(), Integer::sum);
            }
        }
    }

    /**
     * Pairs (i, j), i != j, where the target block of rule i counts as the source material of rule j
     * (A -> B and B -> C).
     */
    static List<int[]> findChains(List<BatchRule> rules, MaterialResolver resolver, Map<BlockState, Item> cache)
    {
        List<int[]> chains = new ArrayList<>();

        for (int i = 0; i < rules.size(); ++i)
        {
            BlockState produced = rules.get(i).target().defaultBlockState();

            if (produced.isAir())
            {
                continue;
            }

            Item producedItem = cache.computeIfAbsent(produced, resolver::sourceItem);

            for (int j = 0; j < rules.size(); ++j)
            {
                if (i != j && producedItem != null && producedItem == rules.get(j).source())
                {
                    chains.add(new int[] { i, j });
                }
            }
        }

        return chains;
    }

    // ---- execution ----

    /**
     * Executes the batch. Nothing is changed if a rule is invalid or a scope can not be resolved.
     */
    public static ReplacementResult execute(LitematicaSchematic schematic, @Nullable SchematicPlacement placement,
                                            List<BatchRule> rules, Scope batchScope, ChainMode mode, MaterialResolver resolver)
    {
        List<Message> errors = validate(schematic, rules, resolver);

        for (Message error : errors)
        {
            if (error != null)
            {
                return ReplacementResult.rejected(error);
            }
        }

        if (mode == ChainMode.ORIGINAL)
        {
            List<ReplacementRule> flat = new ArrayList<>();

            for (BatchRule rule : rules)
            {
                flat.addAll(toReplacementRules(schematic, rule, batchScope, resolver));
            }

            return SchematicReplacer.apply(schematic, flat, placement);
        }

        // Sequential: one pass per rule, each planned on the current state; combined into one undo record
        List<SchematicChange> done = new ArrayList<>();
        Map<String, Integer> replacedPerRegion = new LinkedHashMap<>();
        Set<Message> warnings = new LinkedHashSet<>();

        for (BatchRule rule : rules)
        {
            List<ReplacementRule> passRules = toReplacementRules(schematic, rule, batchScope, resolver);

            if (passRules.isEmpty())
            {
                continue;
            }

            ReplacementResult pass = SchematicReplacer.apply(schematic, passRules, placement);

            if (pass.isSuccess() == false)
            {
                for (int k = done.size() - 1; k >= 0; --k)
                {
                    SchematicReplacer.revert(schematic, done.get(k));
                }

                return pass;
            }

            if (pass.change() != null)
            {
                done.add(pass.change());
                pass.replacedPerRegion().forEach((region, count) -> replacedPerRegion.merge(region, count, Integer::sum));
                warnings.addAll(pass.warnings());
            }
        }

        if (done.isEmpty())
        {
            return new ReplacementResult(Map.of(), List.copyOf(warnings), null, null);
        }

        List<SchematicChange.RegionChange> regions = new ArrayList<>();
        int totalBlocksDelta = 0;

        for (SchematicChange change : done)
        {
            regions.addAll(change.regions());
            totalBlocksDelta += change.totalBlocksDelta();
        }

        return new ReplacementResult(replacedPerRegion, List.copyOf(warnings), null, new SchematicChange(regions, totalBlocksDelta));
    }

    private static List<ReplacementRule> toReplacementRules(LitematicaSchematic schematic, BatchRule rule, Scope batchScope,
                                                            MaterialResolver resolver)
    {
        ItemReplacementPlan plan = ItemReplacementPlan.forItem(schematic, rule.source(), resolver);

        // A rule mapping a state onto itself still takes the position (first match wins), exactly like the analysis
        return plan.rulesFor(rule.target(), rule.keepProperties(), rule.conditions(), rule.overrides(), rule.effectiveScope(batchScope));
    }
}
