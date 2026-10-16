package de.nils.lmswap.core;

import java.util.List;
import java.util.Map;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Part C: counts per property value, live filter sums and previews with overrides.
 */
class FilterPreviewTest
{
    static
    {
        // Before the BlockState constants below: they need registered blocks
        MinecraftTestBootstrap.init();
    }

    private static final BlockState TOP = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    private static final BlockState BOTTOM = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    private static final BlockState DOUBLE = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
    private static final BlockState TOP_WET = TOP.setValue(SlabBlock.WATERLOGGED, true);

    private static LitematicaSchematic schematic;
    private static ItemReplacementPlan plan;

    @BeforeAll
    static void bootstrap()
    {
        MinecraftTestBootstrap.init();
        // 4 top (1 of them wet), 3 bottom, 1 double
        schematic = SchematicReplacerSchematicTest.schematic(new Box(BlockPos.ZERO, new BlockPos(7, 0, 0), "A"));
        LitematicaBlockStateContainer c = schematic.getSubRegionContainer("A");
        BlockState[] row = { TOP, TOP, TOP, TOP_WET, BOTTOM, BOTTOM, BOTTOM, DOUBLE };

        for (int x = 0; x < row.length; ++x)
        {
            c.set(x, 0, 0, row[x]);
        }

        plan = ItemReplacementPlan.forStates(schematic, Items.SMOOTH_STONE_SLAB, List.of(TOP, TOP_WET, BOTTOM, DOUBLE));
    }

    @Test
    void countsPerValue()
    {
        Map<BlockState, Integer> counts = plan.stateCounts(Scope.WHOLE, null);

        assertEquals(Map.of(TOP, 3, TOP_WET, 1, BOTTOM, 3, DOUBLE, 1), counts);
        assertEquals(4, ItemReplacementPlan.valueCount(counts, "type", "top"));
        assertEquals(3, ItemReplacementPlan.valueCount(counts, "type", "bottom"));
        assertEquals(1, ItemReplacementPlan.valueCount(counts, "type", "double"));
        assertEquals(1, ItemReplacementPlan.valueCount(counts, "waterlogged", "true"));
    }

    @Test
    void filterSumsWithoutRescanning()
    {
        Map<BlockState, Integer> counts = plan.stateCounts(Scope.WHOLE, null);

        assertEquals(8, ItemReplacementPlan.filteredCount(counts, List.of()));
        assertEquals(4, ItemReplacementPlan.filteredCount(counts, List.of(PropertyCondition.of("type", "top"))));
        assertEquals(5, ItemReplacementPlan.filteredCount(counts, List.of(PropertyCondition.of("type", "top", "double"))));
        assertEquals(3, ItemReplacementPlan.filteredCount(counts, List.of(PropertyCondition.of("type", "top"),
                                                                          PropertyCondition.of("waterlogged", "false"))));
    }

    @Test
    void sourcePropertiesListAllValues()
    {
        Map<String, List<String>> properties = plan.sourceProperties();

        assertEquals(List.of("top", "bottom", "double"), properties.get("type"));
        assertEquals(List.of("true", "false"), properties.get("waterlogged"));
    }

    @Test
    void overrideWaterloggedTrueSuppressesTheWaterWarning()
    {
        List<PropertyCondition> topOnly = List.of(PropertyCondition.of("type", "top"));

        assertTrue(hasWarning(plan.previewWarnings(Blocks.STONE_SLAB, true, topOnly, Map.of("waterlogged", "false")), "lmswap.warning.water_lost"));
        assertFalse(hasWarning(plan.previewWarnings(Blocks.STONE_SLAB, true, topOnly, Map.of()), "lmswap.warning.water_lost"));
        assertFalse(hasWarning(plan.previewWarnings(Blocks.STONE_SLAB, false, topOnly, Map.of("waterlogged", "true")), "lmswap.warning.water_lost"));
        // Only bottom slabs, none of them wet -> no water warning even when resetting properties
        assertFalse(hasWarning(plan.previewWarnings(Blocks.STONE_SLAB, false, List.of(PropertyCondition.of("type", "bottom")), Map.of()),
                               "lmswap.warning.water_lost"));
    }

    @Test
    void acceptanceOnlyTopSlabsWithOverride()
    {
        LitematicaSchematic s = SchematicReplacerSchematicTest.schematic(new Box(BlockPos.ZERO, new BlockPos(3, 0, 0), "A"));
        LitematicaBlockStateContainer c = s.getSubRegionContainer("A");
        c.set(0, 0, 0, TOP);
        c.set(1, 0, 0, TOP_WET);
        c.set(2, 0, 0, BOTTOM);
        c.set(3, 0, 0, DOUBLE);
        ItemReplacementPlan p = ItemReplacementPlan.forStates(s, Items.SMOOTH_STONE_SLAB, List.of(TOP, TOP_WET, BOTTOM, DOUBLE));

        List<ReplacementRule> rules = p.rulesFor(Blocks.STONE_SLAB, true, List.of(PropertyCondition.of("type", "top")),
                                                 Map.of("waterlogged", "false"), Scope.WHOLE);
        ReplacementResult result = SchematicReplacer.apply(s, rules);

        assertEquals(2, result.totalReplaced());
        BlockState expected = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP).setValue(SlabBlock.WATERLOGGED, false);
        assertEquals(expected, c.get(0, 0, 0));
        assertEquals(expected, c.get(1, 0, 0), "override applied to every replaced block");
        assertEquals(BOTTOM, c.get(2, 0, 0));
        assertEquals(DOUBLE, c.get(3, 0, 0));
    }

    private static boolean hasWarning(List<ReplacementResult.Message> warnings, String key)
    {
        return warnings.stream().anyMatch(m -> m.translationKey().equals(key));
    }
}
