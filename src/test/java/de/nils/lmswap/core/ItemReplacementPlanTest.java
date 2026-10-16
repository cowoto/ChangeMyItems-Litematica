package de.nils.lmswap.core;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
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

class ItemReplacementPlanTest
{
    private static final BlockState WET_SLAB = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true);
    private static final BlockState DOUBLE_SLAB = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);

    private static LitematicaSchematic schematic;

    @BeforeAll
    static void bootstrap()
    {
        MinecraftTestBootstrap.init();
        schematic = SchematicReplacerSchematicTest.schematic(new Box(BlockPos.ZERO, new BlockPos(2, 0, 0), "A"));
        schematic.getSubRegionContainer("A").set(0, 0, 0, WET_SLAB);
        schematic.getSubRegionContainer("A").set(1, 0, 0, DOUBLE_SLAB);
    }

    private static ItemReplacementPlan plan()
    {
        return ItemReplacementPlan.forStates(schematic, Items.SMOOTH_STONE_SLAB, List.of(WET_SLAB, DOUBLE_SLAB));
    }

    private static boolean hasWarning(List<ReplacementResult.Message> warnings, String key)
    {
        return warnings.stream().anyMatch(m -> m.translationKey().equals(key));
    }

    @Test
    void countsAllMatchingBlocks()
    {
        assertEquals(2, plan().getBlockCount());
    }

    @Test
    void targetFilter()
    {
        ItemReplacementPlan plan = plan();

        assertTrue(plan.isAllowedTarget(Blocks.STONE_SLAB, true));
        assertFalse(plan.isAllowedTarget(Blocks.STONE, true), "slab -> full block needs 'keep properties' off");
        assertTrue(plan.isAllowedTarget(Blocks.STONE, false));
        assertFalse(plan.isAllowedTarget(Blocks.SMOOTH_STONE_SLAB, false), "source == target is never offered");
        assertTrue(plan.isAllowedTarget(Blocks.AIR, true));
    }

    @Test
    void waterLostWarningOnlyWhenTheTargetCanNotKeepTheWater()
    {
        ItemReplacementPlan plan = plan();

        assertFalse(hasWarning(plan.previewWarnings(Blocks.STONE_SLAB, true), "lmswap.warning.water_lost"));
        assertTrue(hasWarning(plan.previewWarnings(Blocks.STONE_SLAB, false), "lmswap.warning.water_lost"));
        assertTrue(hasWarning(plan.previewWarnings(Blocks.STONE, false), "lmswap.warning.water_lost"));
        assertTrue(hasWarning(plan.previewWarnings(Blocks.AIR, true), "lmswap.warning.material_removed"));
        assertFalse(hasWarning(plan.previewWarnings(Blocks.AIR, true), "lmswap.warning.water_lost"));
    }

    @Test
    void rulesKeepDoubleSlab()
    {
        List<ReplacementRule> rules = plan().rulesFor(Blocks.STONE_SLAB, true);

        assertEquals(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE),
                     rules.get(1).map(DOUBLE_SLAB).state());
    }
}
