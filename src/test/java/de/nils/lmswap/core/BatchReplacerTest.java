package de.nils.lmswap.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.selection.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import de.nils.lmswap.LmSwapClient;
import de.nils.lmswap.data.RuleSet.ChainMode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Part D: batch analysis and execution. Uses a simple resolver (state -> block item, one item per block) instead of
 * Litematica's MaterialCache, which needs a client world.
 */
class BatchReplacerTest
{
    static
    {
        // Before the BlockState constants below: they need registered blocks
        MinecraftTestBootstrap.init();
    }

    static final MaterialResolver SIMPLE = new MaterialResolver()
    {
        @Override
        public Item sourceItem(BlockState state)
        {
            Item item = state.getBlock().asItem();
            return state.isAir() || item == Items.AIR ? null : item;
        }

        @Override
        public List<MaterialResolver.ItemAmount> materials(BlockState state)
        {
            return state.isAir() ? List.of() : List.of(new MaterialResolver.ItemAmount(state.getBlock().asItem(), 1));
        }
    };

    private static final BlockState OAK_TOP = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    private static final BlockState OAK_BOTTOM = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);

    @BeforeAll
    static void bootstrap()
    {
        MinecraftTestBootstrap.init();
    }

    /** Region A: 3 smooth stone slabs, 2 polished andesite, 2 oak top, 1 oak bottom, 2 stone */
    private static LitematicaSchematic schematic()
    {
        LitematicaSchematic s = SchematicReplacerSchematicTest.schematic(new Box(BlockPos.ZERO, new BlockPos(9, 0, 0), "A"));
        LitematicaBlockStateContainer c = s.getSubRegionContainer("A");
        BlockState[] row = { Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(),
                             Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(),
                             Blocks.POLISHED_ANDESITE.defaultBlockState(), OAK_TOP, OAK_TOP, OAK_BOTTOM,
                             Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState() };

        for (int x = 0; x < row.length; ++x)
        {
            c.set(x, 0, 0, row[x]);
        }

        s.getMetadata().setTotalBlocks(row.length);
        return s;
    }

    private static Block at(LitematicaSchematic s, int x)
    {
        return s.getSubRegionContainer("A").get(x, 0, 0).getBlock();
    }

    private static List<BlockState> snapshot(LitematicaSchematic s)
    {
        List<BlockState> list = new ArrayList<>();

        for (int x = 0; x < 10; ++x)
        {
            list.add(s.getSubRegionContainer("A").get(x, 0, 0));
        }

        return list;
    }

    @Test
    void acceptanceThreeRulesInOnePassAndOneUndo()
    {
        LitematicaSchematic s = schematic();
        List<BlockState> before = snapshot(s);
        List<BatchRule> rules = List.of(
                BatchRule.of(Items.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB),
                BatchRule.of(Items.POLISHED_ANDESITE, Blocks.ANDESITE),
                BatchRule.of(Items.OAK_SLAB, Blocks.SPRUCE_SLAB).withConditions(List.of(PropertyCondition.of("type", "top"))));

        BatchReplacer.Analysis analysis = BatchReplacer.analyze(s, null, rules, Scope.WHOLE, ChainMode.ORIGINAL, SIMPLE);
        assertArrayEquals(new int[] { 3, 2, 2 }, analysis.hits());
        assertFalse(analysis.hasErrors());
        assertTrue(analysis.chains().isEmpty());

        ReplacementResult result = BatchReplacer.execute(s, null, rules, Scope.WHOLE, ChainMode.ORIGINAL, SIMPLE);

        assertTrue(result.isSuccess());
        assertEquals(analysis.totalHits(), result.totalReplaced());
        assertEquals(Blocks.STONE_SLAB, at(s, 0));
        assertEquals(Blocks.ANDESITE, at(s, 3));
        assertEquals(Blocks.SPRUCE_SLAB, at(s, 5));
        assertEquals(Blocks.OAK_SLAB, at(s, 7), "bottom oak slab stays");

        SchematicReplacer.revert(s, result.change());
        assertEquals(before, snapshot(s), "one undo restores everything");
        assertEquals(10, s.getMetadata().getTotalBlocks());
    }

    @Test
    void materialDelta()
    {
        BatchReplacer.Analysis analysis = BatchReplacer.analyze(schematic(), null,
                List.of(BatchRule.of(Items.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB), BatchRule.of(Items.STONE, Blocks.AIR)),
                Scope.WHOLE, ChainMode.ORIGINAL, SIMPLE);

        assertEquals(Map.of(Items.SMOOTH_STONE_SLAB, -3, Items.STONE_SLAB, 3, Items.STONE, -2), analysis.materialDelta());
    }

    @Test
    void chainIsDetectedAndBothModesBehaveAsAnalyzed()
    {
        // smooth stone slab -> stone slab, stone slab -> andesite slab: a chain A -> B -> C
        List<BatchRule> rules = List.of(BatchRule.of(Items.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB),
                                        BatchRule.of(Items.STONE_SLAB, Blocks.ANDESITE_SLAB));

        LitematicaSchematic original = schematic();
        BatchReplacer.Analysis a1 = BatchReplacer.analyze(original, null, rules, Scope.WHOLE, ChainMode.ORIGINAL, SIMPLE);
        assertEquals(1, a1.chains().size());
        assertArrayEquals(new int[] { 0, 1 }, a1.chains().getFirst());
        assertArrayEquals(new int[] { 3, 0 }, a1.hits(), "original mode: rule 2 does not see the new stone slabs");

        ReplacementResult r1 = BatchReplacer.execute(original, null, rules, Scope.WHOLE, ChainMode.ORIGINAL, SIMPLE);
        assertEquals(3, r1.totalReplaced());
        assertEquals(Blocks.STONE_SLAB, at(original, 0));

        LitematicaSchematic sequential = schematic();
        List<BlockState> before = snapshot(sequential);
        BatchReplacer.Analysis a2 = BatchReplacer.analyze(sequential, null, rules, Scope.WHOLE, ChainMode.SEQUENTIAL, SIMPLE);
        assertArrayEquals(new int[] { 3, 3 }, a2.hits(), "sequential mode: rule 2 converts the results of rule 1");

        ReplacementResult r2 = BatchReplacer.execute(sequential, null, rules, Scope.WHOLE, ChainMode.SEQUENTIAL, SIMPLE);
        assertTrue(r2.isSuccess());
        assertEquals(Blocks.ANDESITE_SLAB, at(sequential, 0));
        assertEquals(Map.of(Items.SMOOTH_STONE_SLAB, -3, Items.ANDESITE_SLAB, 3), a2.materialDelta());

        SchematicReplacer.revert(sequential, r2.change());
        assertEquals(before, snapshot(sequential), "two passes, one undo");
    }

    @Test
    void overlappingFiltersAreReportedAsShadowed()
    {
        List<BatchRule> rules = List.of(
                BatchRule.of(Items.OAK_SLAB, Blocks.SPRUCE_SLAB).withConditions(List.of(PropertyCondition.of("type", "top"))),
                BatchRule.of(Items.OAK_SLAB, Blocks.BIRCH_SLAB));

        BatchReplacer.Analysis analysis = BatchReplacer.analyze(schematic(), null, rules, Scope.WHOLE, ChainMode.ORIGINAL, SIMPLE);

        assertArrayEquals(new int[] { 2, 1 }, analysis.hits());
        assertArrayEquals(new int[] { 0, 2 }, analysis.shadowed(), "the two top slabs of rule 2 are taken by rule 1");
    }

    @Test
    void invalidRuleBlocksTheWholeBatch()
    {
        LitematicaSchematic s = schematic();
        List<BlockState> before = snapshot(s);
        List<BatchRule> rules = List.of(BatchRule.of(Items.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB),
                                        BatchRule.of(Items.OAK_SLAB, Blocks.SPRUCE_SLAB).withOverrides(Map.of("type", "sideways")));

        BatchReplacer.Analysis analysis = BatchReplacer.analyze(s, null, rules, Scope.WHOLE, ChainMode.ORIGINAL, SIMPLE);
        assertNull(analysis.ruleErrors().get(0));
        assertNotNull(analysis.ruleErrors().get(1));

        assertFalse(BatchReplacer.execute(s, null, rules, Scope.WHOLE, ChainMode.ORIGINAL, SIMPLE).isSuccess());
        assertEquals(before, snapshot(s));
    }

    @Test
    void ruleScopeOverridesBatchScope()
    {
        LitematicaSchematic s = SchematicReplacerSchematicTest.schematic(new Box(BlockPos.ZERO, new BlockPos(1, 0, 0), "A"),
                                                                         new Box(new BlockPos(5, 0, 0), new BlockPos(6, 0, 0), "B"));
        s.getSubRegionContainer("A").set(0, 0, 0, Blocks.STONE.defaultBlockState());
        s.getSubRegionContainer("B").set(0, 0, 0, Blocks.STONE.defaultBlockState());
        s.getSubRegionContainer("A").set(1, 0, 0, Blocks.DIRT.defaultBlockState());
        s.getSubRegionContainer("B").set(1, 0, 0, Blocks.DIRT.defaultBlockState());

        Scope onlyA = new Scope.Regions(Set.of("A"));
        List<BatchRule> rules = List.of(BatchRule.of(Items.STONE, Blocks.GRANITE),                      // batch scope: A
                                        BatchRule.of(Items.DIRT, Blocks.COARSE_DIRT).withScope(Scope.WHOLE)); // own scope

        BatchReplacer.Analysis analysis = BatchReplacer.analyze(s, null, rules, onlyA, ChainMode.ORIGINAL, SIMPLE);
        assertArrayEquals(new int[] { 1, 2 }, analysis.hits());

        BatchReplacer.execute(s, null, rules, onlyA, ChainMode.ORIGINAL, SIMPLE);
        assertEquals(Blocks.GRANITE, s.getSubRegionContainer("A").get(0, 0, 0).getBlock());
        assertEquals(Blocks.STONE, s.getSubRegionContainer("B").get(0, 0, 0).getBlock());
        assertEquals(Blocks.COARSE_DIRT, s.getSubRegionContainer("B").get(1, 0, 0).getBlock());
    }

    /**
     * Not a pass/fail test: 12 rules on 2 x 256 x 128 x 256 = 16.8 million blocks. Numbers go to docs/TESTS.md.
     */
    @Test
    void performanceTwelveRules()
    {
        Block[] sources = { Blocks.STONE, Blocks.DIRT, Blocks.SMOOTH_STONE_SLAB, Blocks.POLISHED_ANDESITE, Blocks.OAK_PLANKS,
                            Blocks.SPRUCE_PLANKS, Blocks.COBBLESTONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.SAND,
                            Blocks.GRAVEL, Blocks.GLASS };
        Block[] targets = { Blocks.ANDESITE, Blocks.COARSE_DIRT, Blocks.STONE_SLAB, Blocks.CALCITE,
                            Blocks.BIRCH_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.MOSSY_COBBLESTONE, Blocks.POLISHED_GRANITE,
                            Blocks.POLISHED_DIORITE, Blocks.RED_SAND, Blocks.CLAY, Blocks.TINTED_GLASS };
        int size = 256;
        LitematicaSchematic s = SchematicReplacerSchematicTest.schematic(new Box(BlockPos.ZERO, new BlockPos(size - 1, 127, size - 1), "A"),
                                                                         new Box(new BlockPos(size, 0, 0), new BlockPos(2 * size - 1, 127, size - 1), "B"));

        for (String region : List.of("A", "B"))
        {
            LitematicaBlockStateContainer c = s.getSubRegionContainer(region);
            Vec3i v = c.getSize();

            for (int y = 0; y < v.getY(); ++y)
                for (int z = 0; z < v.getZ(); ++z)
                    for (int x = 0; x < v.getX(); ++x)
                        c.set(x, y, z, sources[(x + 3 * y + 7 * z) % sources.length].defaultBlockState());
        }

        List<BatchRule> rules = new ArrayList<>();

        for (int i = 0; i < sources.length; ++i)
        {
            rules.add(BatchRule.of(sources[i].asItem(), targets[i]).withKeepProperties(false));
        }

        long t0 = System.nanoTime();
        BatchReplacer.Analysis analysis = BatchReplacer.analyze(s, null, rules, Scope.WHOLE, ChainMode.ORIGINAL, SIMPLE);
        long t1 = System.nanoTime();
        ReplacementResult result = BatchReplacer.execute(s, null, rules, Scope.WHOLE, ChainMode.ORIGINAL, SIMPLE);
        long t2 = System.nanoTime();
        SchematicReplacer.revert(s, result.change());
        long t3 = System.nanoTime();

        assertEquals(analysis.totalHits(), result.totalReplaced());
        assertEquals(2L * size * 128 * size, result.totalReplaced());

        String line = String.format("[lmswap-perf] batch rules=%d volume=%d replaced=%d analyze=%dms execute=%dms undo=%dms",
                                    rules.size(), 2L * size * 128 * size, result.totalReplaced(),
                                    (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000);
        LmSwapClient.LOGGER.info(line);
        System.out.println(line);
    }
}
