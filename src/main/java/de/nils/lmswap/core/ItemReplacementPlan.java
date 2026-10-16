package de.nils.lmswap.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.materials.MaterialCache;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.ILitematicaBlockStatePalette;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import de.nils.lmswap.core.ReplacementResult.Message;

/**
 * Translates a material list entry (an item, see docs/DECISIONS.md decision 1) into the block states of a
 * schematic that need that item, and builds the replacement rules for a chosen target block.
 * <p>
 * A state belongs to the item if Litematica's {@link MaterialCache} names it as the build item, using the same
 * rules as the material list (waterlogged states count as their dry state). States that need several items
 * (potted plants) are skipped, replacing them would also remove the other item.
 */
public final class ItemReplacementPlan
{
    private final LitematicaSchematic schematic;
    private final Item item;
    private final List<BlockState> sourceStates;
    private final Set<Block> sourceBlocks;
    private final int blockCount;

    private ItemReplacementPlan(LitematicaSchematic schematic, Item item, List<BlockState> sourceStates)
    {
        this.schematic = schematic;
        this.item = item;
        this.sourceStates = List.copyOf(sourceStates);
        this.sourceBlocks = new LinkedHashSet<>();
        sourceStates.forEach(state -> this.sourceBlocks.add(state.getBlock()));
        this.blockCount = SchematicReplacer.countMatching(schematic, this.rulesFor(null, true));
    }

    public static ItemReplacementPlan forItem(LitematicaSchematic schematic, Item item)
    {
        MaterialCache cache = MaterialCache.getInstance();
        Set<BlockState> states = new LinkedHashSet<>();

        for (String regionName : schematic.getAreas().keySet())
        {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);

            if (container == null)
            {
                continue;
            }

            ILitematicaBlockStatePalette palette = container.getPalette();

            for (int id = 0; id < palette.getPaletteSize(); ++id)
            {
                BlockState state = palette.getBlockState(id);

                if (state != null && state.isAir() == false && states.contains(state) == false
                    && cache.requiresMultipleItems(state) == false)
                {
                    ItemStack stack = cache.getRequiredBuildItemForState(dryState(state));

                    if (stack.isEmpty() == false && stack.getItem() == item)
                    {
                        states.add(state);
                    }
                }
            }
        }

        return new ItemReplacementPlan(schematic, item, new ArrayList<>(states));
    }

    /** For tests: a plan with given source states, without the item lookup through Litematica's MaterialCache */
    static ItemReplacementPlan forStates(LitematicaSchematic schematic, Item item, List<BlockState> states)
    {
        return new ItemReplacementPlan(schematic, item, states);
    }

    public LitematicaSchematic getSchematic()
    {
        return this.schematic;
    }

    public Item getItem()
    {
        return this.item;
    }

    public boolean isEmpty()
    {
        return this.sourceStates.isEmpty();
    }

    public Set<Block> getSourceBlocks()
    {
        return this.sourceBlocks;
    }

    /** Number of blocks in all regions of the schematic that would be replaced */
    public int getBlockCount()
    {
        return this.blockCount;
    }

    /**
     * Decision 3 (strict): when properties are kept, the target must define the same properties as every source
     * block. Without keeping properties every block is allowed (it gets its default state). Air is always allowed.
     */
    public boolean isAllowedTarget(Block target, boolean keepProperties)
    {
        if (target.defaultBlockState().isAir())
        {
            return true;
        }

        if (this.sourceBlocks.contains(target))
        {
            return false;
        }

        if (keepProperties == false)
        {
            return true;
        }

        for (Block source : this.sourceBlocks)
        {
            if (BlockStateMapper.haveSameProperties(source, target) == false)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * @param target null only for counting (the target is irrelevant for matching)
     */
    public List<ReplacementRule> rulesFor(@Nullable Block target, boolean keepProperties)
    {
        return this.rulesFor(target, keepProperties, List.of(), Map.of(), Scope.WHOLE);
    }

    /**
     * One rule per source state that passes the conditions. The conditions are evaluated here (a state that lacks a
     * filtered property does not pass), so the rules themselves are exact-state rules carrying overrides and scope.
     *
     * @param target null only for counting (the target is irrelevant for matching)
     */
    public List<ReplacementRule> rulesFor(@Nullable Block target, boolean keepProperties, List<PropertyCondition> conditions,
                                          Map<String, String> overrides, Scope scope)
    {
        Block t = target != null ? target : Blocks.AIR;
        List<ReplacementRule> rules = new ArrayList<>(this.sourceStates.size());

        for (BlockState state : this.sourceStates)
        {
            if (conditions.stream().allMatch(c -> c.matches(state)))
            {
                rules.add(ReplacementRule.ofState(state, t).withKeepProperties(keepProperties)
                                         .withOverrides(target != null ? overrides : Map.of()).withScope(scope));
            }
        }

        return rules;
    }

    /**
     * A condition is valid if at least one source block has the property and knows all values (several source
     * blocks, e.g. torch and wall torch, can have different properties).
     *
     * @return the first problem, or null if all conditions are valid
     */
    @Nullable
    public Message validateConditions(List<PropertyCondition> conditions)
    {
        for (PropertyCondition condition : conditions)
        {
            Message firstError = null;
            boolean valid = false;

            for (Block block : this.sourceBlocks)
            {
                Message error = condition.validate(block);

                if (error == null)
                {
                    valid = true;
                    break;
                }

                firstError = firstError != null ? firstError : error;
            }

            if (valid == false && firstError != null)
            {
                return firstError;
            }
        }

        return null;
    }

    /**
     * Warnings shown before replacing, derived from {@link BlockStateMapper} without changing anything.
     */
    public List<Message> previewWarnings(Block target, boolean keepProperties)
    {
        return this.previewWarnings(target, keepProperties, List.of(), Map.of());
    }

    /**
     * Warnings for the states that pass the conditions, with the overrides applied (e.g. waterlogged=true keeps the
     * water even if properties are not kept).
     */
    public List<Message> previewWarnings(Block target, boolean keepProperties, List<PropertyCondition> conditions,
                                         Map<String, String> overrides)
    {
        Set<Message> warnings = new LinkedHashSet<>();
        boolean targetIsAir = target.defaultBlockState().isAir();
        boolean targetHasBlockEntity = target.defaultBlockState().hasBlockEntity();

        if (targetIsAir)
        {
            warnings.add(Message.of("lmswap.warning.material_removed"));
        }

        for (BlockState state : this.sourceStates)
        {
            if (conditions.stream().allMatch(c -> c.matches(state)) == false)
            {
                continue;
            }

            if (targetIsAir == false)
            {
                if (keepProperties)
                {
                    for (String property : BlockStateMapper.map(state, target).droppedProperties())
                    {
                        warnings.add(Message.of("lmswap.warning.property_lost", property));
                    }
                }
                else if (state.getProperties().isEmpty() == false)
                {
                    warnings.add(Message.of("lmswap.warning.properties_reset"));
                }
            }

            if (state.hasBlockEntity() && targetHasBlockEntity == false)
            {
                warnings.add(Message.of("lmswap.warning.block_entity_data_lost"));
            }

            if (targetIsAir == false && isWaterlogged(state))
            {
                BlockState mapped = ReplacementRule.ofState(state, target).withKeepProperties(keepProperties)
                                                   .withOverrides(overrides).map(state).state();

                if (isWaterlogged(mapped) == false)
                {
                    warnings.add(Message.of("lmswap.warning.water_lost"));
                }
            }
        }

        return List.copyOf(warnings);
    }

    /**
     * Properties of the source blocks with all their possible value names, for the filter GUI. With several source
     * blocks (torch / wall torch) this is the union.
     */
    public Map<String, List<String>> sourceProperties()
    {
        Map<String, List<String>> properties = new java.util.LinkedHashMap<>();

        for (Block block : this.sourceBlocks)
        {
            for (net.minecraft.world.level.block.state.properties.Property<?> prop : block.getStateDefinition().getProperties())
            {
                List<String> values = properties.computeIfAbsent(prop.getName(), k -> new ArrayList<>());

                for (String value : valueNames(prop))
                {
                    if (values.contains(value) == false)
                    {
                        values.add(value);
                    }
                }
            }
        }

        return properties;
    }

    private static <T extends Comparable<T>> List<String> valueNames(net.minecraft.world.level.block.state.properties.Property<T> prop)
    {
        return prop.getPossibleValues().stream().map(prop::getName).toList();
    }

    /**
     * Blocks per source state in the scope; filter counts are sums over this map.
     */
    public Map<BlockState, Integer> stateCounts(Scope scope, @Nullable fi.dy.masa.litematica.schematic.placement.SchematicPlacement placement)
    {
        return SchematicReplacer.countPerState(this.schematic, this.rulesFor(null, true, List.of(), Map.of(), scope), placement);
    }

    /** Sum of the counts of the states that pass all conditions */
    public static int filteredCount(Map<BlockState, Integer> stateCounts, List<PropertyCondition> conditions)
    {
        int sum = 0;

        for (Map.Entry<BlockState, Integer> e : stateCounts.entrySet())
        {
            if (conditions.stream().allMatch(c -> c.matches(e.getKey())))
            {
                sum += e.getValue();
            }
        }

        return sum;
    }

    /** Count of blocks having {@code property=value}, regardless of other filters */
    public static int valueCount(Map<BlockState, Integer> stateCounts, String property, String value)
    {
        return filteredCount(stateCounts, List.of(PropertyCondition.of(property, value)));
    }

    private static boolean isWaterlogged(BlockState state)
    {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    private static BlockState dryState(BlockState state)
    {
        if (isWaterlogged(state))
        {
            return state.setValue(BlockStateProperties.WATERLOGGED, false);
        }

        return state;
    }
}
