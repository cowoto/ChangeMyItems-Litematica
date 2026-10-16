package de.nils.lmswap.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.selection.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Part A: source filters, target overrides, region scopes and their validation.
 */
class FilterOverrideScopeTest
{
    static
    {
        // Before the BlockState constants below: they need registered blocks
        MinecraftTestBootstrap.init();
    }

    private static final BlockState TOP = slab(Blocks.SMOOTH_STONE_SLAB, SlabType.TOP, false);
    private static final BlockState BOTTOM = slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM, false);
    private static final BlockState DOUBLE = slab(Blocks.SMOOTH_STONE_SLAB, SlabType.DOUBLE, false);
    private static final BlockState TOP_WET = slab(Blocks.SMOOTH_STONE_SLAB, SlabType.TOP, true);

    @BeforeAll
    static void bootstrap()
    {
        MinecraftTestBootstrap.init();
    }

    private static BlockState slab(net.minecraft.world.level.block.Block block, SlabType type, boolean wet)
    {
        return block.defaultBlockState().setValue(SlabBlock.TYPE, type).setValue(SlabBlock.WATERLOGGED, wet);
    }

    /** Region A: TOP, BOTTOM, DOUBLE, TOP_WET in a row; region B: TOP, BOTTOM */
    private static LitematicaSchematic schematic()
    {
        LitematicaSchematic schematic = SchematicReplacerSchematicTest.schematic(
                new Box(BlockPos.ZERO, new BlockPos(3, 0, 0), "A"),
                new Box(new BlockPos(10, 0, 0), new BlockPos(11, 0, 0), "B"));
        LitematicaBlockStateContainer a = schematic.getSubRegionContainer("A");
        LitematicaBlockStateContainer b = schematic.getSubRegionContainer("B");
        a.set(0, 0, 0, TOP);
        a.set(1, 0, 0, BOTTOM);
        a.set(2, 0, 0, DOUBLE);
        a.set(3, 0, 0, TOP_WET);
        b.set(0, 0, 0, TOP);
        b.set(1, 0, 0, BOTTOM);
        schematic.getMetadata().setTotalBlocks(6);
        return schematic;
    }

    private static BlockState at(LitematicaSchematic schematic, String region, int x)
    {
        return schematic.getSubRegionContainer(region).get(x, 0, 0);
    }

    @Test
    void filterTypeTopReplacesOnlyTopSlabs()
    {
        LitematicaSchematic schematic = schematic();
        ReplacementRule rule = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB)
                                              .withConditions(List.of(PropertyCondition.of("type", "top")));

        ReplacementResult result = SchematicReplacer.apply(schematic, rule);

        assertTrue(result.isSuccess());
        assertEquals(3, result.totalReplaced());
        assertEquals(slab(Blocks.STONE_SLAB, SlabType.TOP, false), at(schematic, "A", 0));
        assertEquals(BOTTOM, at(schematic, "A", 1));
        assertEquals(DOUBLE, at(schematic, "A", 2));
        assertEquals(slab(Blocks.STONE_SLAB, SlabType.TOP, true), at(schematic, "A", 3));
        assertEquals(slab(Blocks.STONE_SLAB, SlabType.TOP, false), at(schematic, "B", 0));
        assertEquals(BOTTOM, at(schematic, "B", 1));
    }

    @Test
    void filterWithSeveralValues()
    {
        LitematicaSchematic schematic = schematic();
        ReplacementRule rule = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB)
                                              .withConditions(List.of(PropertyCondition.of("type", "top", "double"),
                                                                      PropertyCondition.of("waterlogged", "false")));

        assertEquals(3, SchematicReplacer.countMatching(schematic, List.of(rule)));
        assertEquals(3, SchematicReplacer.apply(schematic, rule).totalReplaced()); // A0, A2, B0 (A3 is wet)
        assertEquals(TOP_WET, at(schematic, "A", 3));
    }

    @Test
    void filterOnUnknownPropertyIsAValidationError()
    {
        LitematicaSchematic schematic = schematic();
        ReplacementRule rule = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB)
                                              .withConditions(List.of(PropertyCondition.of("facing", "north")));

        assertEquals("lmswap.error.filter_unknown_property", rule.validate().getFirst().translationKey());

        ReplacementResult result = SchematicReplacer.apply(schematic, rule);

        assertFalse(result.isSuccess());
        assertEquals("lmswap.error.filter_unknown_property", result.error().translationKey());
        assertEquals(TOP, at(schematic, "A", 0));
    }

    @Test
    void filterWithUnknownValueIsAValidationError()
    {
        ReplacementRule rule = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB)
                                              .withConditions(List.of(PropertyCondition.of("type", "sideways")));

        assertEquals("lmswap.error.filter_invalid_value", rule.validate().getFirst().translationKey());
    }

    @Test
    void overrideWaterloggedFalseDriesWaterloggedSlabs()
    {
        LitematicaSchematic schematic = schematic();
        ReplacementRule rule = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB)
                                              .withOverrides(Map.of("waterlogged", "false"));

        assertTrue(rule.validate().isEmpty());
        assertTrue(SchematicReplacer.apply(schematic, rule).isSuccess());

        assertEquals(slab(Blocks.STONE_SLAB, SlabType.TOP, false), at(schematic, "A", 3), "type kept, water removed");
        assertEquals(slab(Blocks.STONE_SLAB, SlabType.DOUBLE, false), at(schematic, "A", 2));
    }

    @Test
    void overrideAlsoAppliesWithoutKeepingProperties()
    {
        ReplacementRule rule = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB)
                                              .withKeepProperties(false)
                                              .withOverrides(Map.of("type", "top"));

        assertEquals(slab(Blocks.STONE_SLAB, SlabType.TOP, false), rule.map(BOTTOM).state());
    }

    @Test
    void overrideWithUnknownValueOrPropertyIsAValidationError()
    {
        ReplacementRule badValue = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB)
                                                  .withOverrides(Map.of("type", "sideways"));
        ReplacementRule badProperty = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB)
                                                     .withOverrides(Map.of("facing", "north"));
        LitematicaSchematic schematic = schematic();

        assertEquals("lmswap.error.override_invalid_value", badValue.validate().getFirst().translationKey());
        assertEquals("lmswap.error.override_unknown_property", badProperty.validate().getFirst().translationKey());
        assertFalse(SchematicReplacer.apply(schematic, badValue).isSuccess());
        assertEquals(TOP, at(schematic, "A", 0));
    }

    @Test
    void regionScopeOnlyTouchesTheNamedRegions()
    {
        LitematicaSchematic schematic = schematic();
        ReplacementRule rule = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB)
                                              .withScope(new Scope.Regions(Set.of("B")));

        ReplacementResult result = SchematicReplacer.apply(schematic, rule);

        assertEquals(Map.of("B", 2), result.replacedPerRegion());
        assertEquals(TOP, at(schematic, "A", 0));
        assertEquals(Blocks.STONE_SLAB, at(schematic, "B", 0).getBlock());
    }

    @Test
    void worldBoxScopeIsRejectedWithoutPlacement()
    {
        ReplacementRule rule = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB)
                                              .withScope(new Scope.WorldBoxes(List.of(Scope.WorldBox.of(BlockPos.ZERO, BlockPos.ZERO))));

        ReplacementResult result = SchematicReplacer.apply(schematic(), rule);

        assertFalse(result.isSuccess());
        assertEquals("lmswap.error.scope_unresolvable", result.error().translationKey());
    }

    @Test
    void firstMatchingRuleWinsAndEveryPositionIsReplacedOnce()
    {
        LitematicaSchematic schematic = schematic();
        List<ReplacementRule> rules = List.of(
                ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB)
                               .withConditions(List.of(PropertyCondition.of("type", "top"))),
                ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.OAK_SLAB),
                // Would turn the new stone slabs into birch slabs if rules saw each other's results
                ReplacementRule.ofBlock(Blocks.STONE_SLAB, Blocks.BIRCH_SLAB));

        ReplacementResult result = SchematicReplacer.apply(schematic, rules);

        assertEquals(6, result.totalReplaced());
        assertEquals(Blocks.STONE_SLAB, at(schematic, "A", 0).getBlock());
        assertEquals(Blocks.OAK_SLAB, at(schematic, "A", 1).getBlock());
        assertEquals(Blocks.OAK_SLAB, at(schematic, "A", 2).getBlock());
        assertEquals(Blocks.STONE_SLAB, at(schematic, "A", 3).getBlock());

        SchematicReplacer.revert(schematic, result.change());
        assertEquals(TOP, at(schematic, "A", 0));
        assertEquals(TOP_WET, at(schematic, "A", 3));
        assertEquals(6, schematic.getMetadata().getTotalBlocks());
    }

    @Test
    void planAppliesConditionsPerStateAndValidatesThem()
    {
        LitematicaSchematic schematic = schematic();
        ItemReplacementPlan plan = ItemReplacementPlan.forStates(schematic, Items.SMOOTH_STONE_SLAB, List.of(TOP, BOTTOM, DOUBLE, TOP_WET));

        List<ReplacementRule> topOnly = plan.rulesFor(Blocks.STONE_SLAB, true, List.of(PropertyCondition.of("type", "top")),
                                                      Map.of("waterlogged", "false"), Scope.WHOLE);

        assertEquals(2, topOnly.size());
        assertEquals(3, SchematicReplacer.countMatching(schematic, topOnly));
        assertNull(plan.validateConditions(List.of(PropertyCondition.of("type", "top"))));
        assertNotNull(plan.validateConditions(List.of(PropertyCondition.of("facing", "north"))));
    }

    @Test
    void plainRuleBehavesAsBefore()
    {
        ReplacementRule plain = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB);

        assertTrue(plain.conditions().isEmpty());
        assertTrue(plain.overrides().isEmpty());
        assertEquals(Scope.WHOLE, plain.scope());
        assertTrue(plain.validate().isEmpty());
    }
}
