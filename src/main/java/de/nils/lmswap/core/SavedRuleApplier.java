package de.nils.lmswap.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import de.nils.lmswap.data.RuleStore;
import de.nils.lmswap.data.SavedRule;

/**
 * Applies the saved rules to a loaded schematic: first the rules of its file, then the global rules for source items
 * that have no schematic specific rule. Every applied rule becomes a normal undo history entry.
 */
public final class SavedRuleApplier
{
    private SavedRuleApplier()
    {
    }

    /**
     * @param appliedRules number of rules that changed something
     * @param blocks       total replaced blocks
     * @param skipped      rules that could not be applied (unknown ids, invalid filter/override, strict property check)
     * @param skippedSelection rules with scope "area selection", which is not reliably available when loading
     */
    public record Summary(int appliedRules, int blocks, int skipped, int skippedSelection)
    {
    }

    /**
     * The rules that apply to this schematic, in application order: first its own rules, then the global rules. A
     * global rule is left out only if a schematic rule has the same source, filter and scope. Otherwise both run:
     * e.g. "top slabs -> X" for this schematic and "all slabs -> Y" globally; the remaining slabs become Y.
     */
    public static List<SavedRule> effectiveRules(LitematicaSchematic schematic)
    {
        List<SavedRule> rules = new ArrayList<>(RuleStore.INSTANCE.getSchematicRules(schematic));
        Set<List<Object>> covered = new HashSet<>();
        rules.forEach(rule -> covered.add(key(rule)));

        for (SavedRule rule : RuleStore.INSTANCE.getGlobalRules())
        {
            if (covered.contains(key(rule)) == false)
            {
                rules.add(rule);
            }
        }

        return rules;
    }

    private static List<Object> key(SavedRule rule)
    {
        return List.of(rule.sourceItem(), rule.conditions(), rule.scope());
    }

    public static Summary apply(LitematicaSchematic schematic)
    {
        int applied = 0;
        int blocks = 0;
        int skipped = 0;
        int skippedSelection = 0;

        for (SavedRule rule : effectiveRules(schematic))
        {
            Optional<Scope> scope = rule.scope().toScope();

            if (scope.isEmpty())
            {
                ++skippedSelection;
                continue;
            }

            Optional<Item> source = rule.resolveSource();
            Optional<Block> target = rule.resolveTarget();

            if (source.isEmpty() || target.isEmpty())
            {
                ++skipped;
                continue;
            }

            ItemReplacementPlan plan = ItemReplacementPlan.forItem(schematic, source.get());

            if (plan.isEmpty() == false && plan.validateConditions(rule.propertyConditions()) != null)
            {
                ++skipped;
                continue;
            }

            // Source == target (e.g. a rule edited by hand): nothing to do, not an error
            if (plan.isEmpty() || plan.getSourceBlocks().contains(target.get()))
            {
                continue;
            }

            // Same restriction as the target list in the GUI: only blocks with an item (Easy Place needs one), or air
            if (target.get().asItem() == Items.AIR && target.get().defaultBlockState().isAir() == false)
            {
                ++skipped;
                continue;
            }

            if (plan.isAllowedTarget(target.get(), rule.keepProperties()) == false)
            {
                ++skipped;
                continue;
            }

            List<ReplacementRule> rules = plan.rulesFor(target.get(), rule.keepProperties(), rule.propertyConditions(),
                                                        rule.overrides(), scope.get());

            if (rules.isEmpty())
            {
                continue;
            }

            ReplacementResult result = MaterialSwapService.replaceInSchematic(schematic, rules, rule.sourceName(), rule.targetName());

            if (result.isSuccess() && result.totalReplaced() > 0)
            {
                ++applied;
                blocks += result.totalReplaced();
            }
            else if (result.isSuccess() == false)
            {
                ++skipped;
            }
        }

        return new Summary(applied, blocks, skipped, skippedSelection);
    }
}
