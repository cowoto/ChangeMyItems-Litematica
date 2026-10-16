package de.nils.lmswap.core;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import de.nils.lmswap.LmSwapClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge cases on a real {@link LitematicaSchematic} (several regions, block entities, metadata) and a performance
 * measurement. See docs/TESTS.md.
 */
class SchematicReplacerSchematicTest
{
    @BeforeAll
    static void bootstrap()
    {
        MinecraftTestBootstrap.init();
    }

    static LitematicaSchematic schematic(Box... boxes)
    {
        AreaSelection area = new AreaSelection();

        for (Box box : boxes)
        {
            area.addSubRegionBox(box, false);
        }

        LitematicaSchematic schematic = LitematicaSchematic.createEmptySchematic(area, "test");
        assertNotNull(schematic);
        return schematic;
    }


    @Test
    void doubleSlabStaysDoubleAndWaterloggedStaysAcrossRegions()
    {
        LitematicaSchematic schematic = schematic(new Box(BlockPos.ZERO, new BlockPos(3, 1, 1), "A"),
                                                  new Box(new BlockPos(10, 0, 0), new BlockPos(11, 0, 0), "B"));
        LitematicaBlockStateContainer a = schematic.getSubRegionContainer("A");
        LitematicaBlockStateContainer b = schematic.getSubRegionContainer("B");
        BlockState dbl = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        BlockState wet = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true);
        a.set(0, 0, 0, dbl);
        a.set(1, 1, 1, wet);
        b.set(0, 0, 0, dbl);
        schematic.getMetadata().setTotalBlocks(3);

        ReplacementResult result = SchematicReplacer.apply(schematic, ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB));

        assertTrue(result.isSuccess());
        assertEquals(Map.of("A", 2, "B", 1), result.replacedPerRegion());
        assertEquals(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), a.get(0, 0, 0));
        assertEquals(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true), a.get(1, 1, 1));
        assertEquals(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), b.get(0, 0, 0));
        assertEquals(3, schematic.getMetadata().getTotalBlocks());

        SchematicReplacer.revert(schematic, result.change());

        assertEquals(dbl, a.get(0, 0, 0));
        assertEquals(wet, a.get(1, 1, 1));
        assertEquals(dbl, b.get(0, 0, 0));
    }

    @Test
    void waterloggedToTargetWithoutWaterloggedLosesWaterOnlyWithoutKeepingProperties()
    {
        LitematicaSchematic schematic = schematic(new Box(BlockPos.ZERO, new BlockPos(1, 0, 0), "A"));
        schematic.getSubRegionContainer("A").set(0, 0, 0, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true));

        // Strict (default): slab -> full block is rejected, nothing changes
        ReplacementResult strict = SchematicReplacer.apply(schematic, ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE));
        assertFalse(strict.isSuccess());
        assertEquals(Blocks.SMOOTH_STONE_SLAB, schematic.getSubRegionContainer("A").get(0, 0, 0).getBlock());

        // Without keeping properties the target gets its default state; the water is gone
        ReplacementResult reset = SchematicReplacer.apply(schematic,
                ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE).withKeepProperties(false));
        assertTrue(reset.isSuccess());
        assertEquals(Blocks.STONE.defaultBlockState(), schematic.getSubRegionContainer("A").get(0, 0, 0));
    }

    @Test
    void blockEntityDataIsRemovedOrKeptAndRestoredByUndo()
    {
        LitematicaSchematic schematic = schematic(new Box(BlockPos.ZERO, new BlockPos(2, 0, 0), "A"));
        LitematicaBlockStateContainer c = schematic.getSubRegionContainer("A");
        Map<BlockPos, CompoundData> blockEntities = schematic.getBlockEntityMapForRegion("A");

        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST);
        BlockState sign = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 5);
        c.set(0, 0, 0, chest);
        c.set(1, 0, 0, sign);
        CompoundData chestData = new CompoundData().putString("id", "minecraft:chest");
        CompoundData signData = new CompoundData().putString("id", "minecraft:sign");
        blockEntities.put(new BlockPos(0, 0, 0), chestData);
        blockEntities.put(new BlockPos(1, 0, 0), signData);
        schematic.getMetadata().setTotalBlocks(2);

        // Chest -> air: material removed, block entity data removed with a warning, total blocks decremented
        ReplacementResult removed = SchematicReplacer.apply(schematic, ReplacementRule.ofBlock(Blocks.CHEST, Blocks.AIR));
        assertTrue(c.get(0, 0, 0).isAir());
        assertNull(blockEntities.get(new BlockPos(0, 0, 0)));
        assertTrue(removed.warnings().stream().anyMatch(m -> m.translationKey().equals("lmswap.warning.block_entities_removed")));
        assertEquals(1, schematic.getMetadata().getTotalBlocks());

        // Oak sign -> spruce sign: both have a sign block entity, the text data is kept (with a hint)
        ReplacementResult kept = SchematicReplacer.apply(schematic, ReplacementRule.ofBlock(Blocks.OAK_SIGN, Blocks.SPRUCE_SIGN));
        assertEquals(Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 5), c.get(1, 0, 0));
        assertEquals(signData, blockEntities.get(new BlockPos(1, 0, 0)));
        assertTrue(kept.warnings().stream().anyMatch(m -> m.translationKey().equals("lmswap.warning.block_entities_kept")));

        SchematicReplacer.revert(schematic, kept.change());
        SchematicReplacer.revert(schematic, removed.change());

        assertEquals(chest, c.get(0, 0, 0));
        assertEquals(sign, c.get(1, 0, 0));
        assertEquals(chestData, blockEntities.get(new BlockPos(0, 0, 0)));
        assertEquals(2, schematic.getMetadata().getTotalBlocks());
    }

    @Test
    void sourceEqualsTargetChangesNothing()
    {
        LitematicaSchematic schematic = schematic(new Box(BlockPos.ZERO, new BlockPos(1, 0, 0), "A"));
        schematic.getSubRegionContainer("A").set(0, 0, 0, Blocks.STONE_SLAB.defaultBlockState());

        ReplacementResult result = SchematicReplacer.apply(schematic, ReplacementRule.ofBlock(Blocks.STONE_SLAB, Blocks.STONE_SLAB));

        assertTrue(result.isSuccess());
        assertEquals(0, result.totalReplaced());
        assertNull(result.change());
    }

    /**
     * Not a pass/fail test: measures replace, count and undo on a large schematic. The numbers go to docs/TESTS.md.
     */
    @Test
    void performanceLargeSchematic()
    {
        int size = 256;   // 2 regions of 256 x 128 x 256 = 16.8 million blocks in total
        LitematicaSchematic schematic = schematic(new Box(BlockPos.ZERO, new BlockPos(size - 1, 127, size - 1), "A"),
                                                  new Box(new BlockPos(size, 0, 0), new BlockPos(2 * size - 1, 127, size - 1), "B"));
        BlockState[] states = { Blocks.STONE.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(),
                                Blocks.DIRT.defaultBlockState(), Blocks.AIR.defaultBlockState() };

        for (String region : List.of("A", "B"))
        {
            LitematicaBlockStateContainer c = schematic.getSubRegionContainer(region);

            for (int y = 0; y < 128; ++y)
                for (int z = 0; z < size; ++z)
                    for (int x = 0; x < size; ++x)
                        c.set(x, y, z, states[(x + y + z) % states.length]);
        }

        List<ReplacementRule> rules = List.of(ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB));

        long t0 = System.nanoTime();
        int count = SchematicReplacer.countMatching(schematic, rules);
        long t1 = System.nanoTime();
        ReplacementResult result = SchematicReplacer.apply(schematic, rules);
        long t2 = System.nanoTime();
        SchematicReplacer.revert(schematic, result.change());
        long t3 = System.nanoTime();

        assertEquals(count, result.totalReplaced());
        assertTrue(count > 4_000_000);

        LmSwapClient.LOGGER.info("[lmswap-perf] volume={} replaced={} count={}ms apply={}ms revert={}ms",
                                 2L * size * 128 * size, count, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000);
        System.out.printf("[lmswap-perf] volume=%d replaced=%d count=%dms apply=%dms revert=%dms%n",
                          2L * size * 128 * size, count, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000);
    }
}
