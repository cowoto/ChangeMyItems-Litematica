package de.nils.lmswap.core;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicReplacerContainerTest
{
    @BeforeAll
    static void bootstrap()
    {
        MinecraftTestBootstrap.init();
    }

    @Test
    void replacesAllStatesOfBlockAndReverts()
    {
        // 2-bit linear palette holds 4 states: air + 3 used here, so the target state forces a resize
        LitematicaBlockStateContainer container = new LitematicaBlockStateContainer(4, 3, 2);
        BlockState slabTop = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        BlockState slabBottom = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        container.set(0, 0, 0, slabTop);
        container.set(3, 2, 1, slabBottom);
        container.set(1, 1, 1, Blocks.DIRT.defaultBlockState());
        container.set(2, 1, 0, slabTop);

        ReplacementRule rule = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB);
        Set<ReplacementResult.Message> warnings = new LinkedHashSet<>();
        SchematicReplacer.ContainerChange change = SchematicReplacer.replaceInContainer(container, rule, warnings);

        assertEquals(3, change.count());
        assertEquals(0, change.totalBlocksDelta());
        assertTrue(warnings.isEmpty());
        assertEquals(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), container.get(0, 0, 0));
        assertEquals(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), container.get(2, 1, 0));
        assertEquals(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), container.get(3, 2, 1));
        assertEquals(Blocks.DIRT.defaultBlockState(), container.get(1, 1, 1));
        assertEquals(Blocks.AIR.defaultBlockState(), container.get(0, 2, 1));

        SchematicReplacer.revertContainer(container, change.oldStates());

        assertEquals(slabTop, container.get(0, 0, 0));
        assertEquals(slabTop, container.get(2, 1, 0));
        assertEquals(slabBottom, container.get(3, 2, 1));
        assertEquals(Blocks.DIRT.defaultBlockState(), container.get(1, 1, 1));
    }

    @Test
    void exactStateRuleOnlyReplacesThatState()
    {
        LitematicaBlockStateContainer container = new LitematicaBlockStateContainer(2, 1, 1);
        BlockState slabTop = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        BlockState slabBottom = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        container.set(0, 0, 0, slabTop);
        container.set(1, 0, 0, slabBottom);

        SchematicReplacer.ContainerChange change = SchematicReplacer.replaceInContainer(
                container, ReplacementRule.ofState(slabTop, Blocks.BIRCH_SLAB), new LinkedHashSet<>());

        assertEquals(1, change.count());
        assertEquals(Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), container.get(0, 0, 0));
        assertEquals(slabBottom, container.get(1, 0, 0));
    }

    @Test
    void airAsTargetRemovesMaterial()
    {
        LitematicaBlockStateContainer container = new LitematicaBlockStateContainer(2, 2, 2);
        container.set(0, 0, 0, Blocks.DIRT.defaultBlockState());
        container.set(1, 1, 1, Blocks.DIRT.defaultBlockState());

        SchematicReplacer.ContainerChange change = SchematicReplacer.replaceInContainer(
                container, ReplacementRule.ofBlock(Blocks.DIRT, Blocks.AIR), new LinkedHashSet<>());

        assertEquals(2, change.count());
        assertEquals(-2, change.totalBlocksDelta());
        assertTrue(container.get(1, 1, 1).isAir());
    }

    @Test
    void strictRuleRejectsDifferentProperties()
    {
        assertFalse(ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE).isPropertyCompatible());
        assertTrue(ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE).withRequireSameProperties(false).isPropertyCompatible());
        assertTrue(ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.AIR).isPropertyCompatible());
    }

    @Test
    void airAsSourceIsForbidden()
    {
        assertThrows(IllegalArgumentException.class, () -> ReplacementRule.ofBlock(Blocks.AIR, Blocks.STONE));
        assertThrows(IllegalArgumentException.class, () -> ReplacementRule.ofBlock(Blocks.CAVE_AIR, Blocks.STONE));
    }

    @Test
    void noMatchLeavesContainerUntouched()
    {
        LitematicaBlockStateContainer container = new LitematicaBlockStateContainer(1, 1, 1);
        container.set(0, 0, 0, Blocks.DIRT.defaultBlockState());

        SchematicReplacer.ContainerChange change = SchematicReplacer.replaceInContainer(
                container, ReplacementRule.ofBlock(Blocks.STONE, Blocks.GRANITE), new LinkedHashSet<>());

        assertEquals(0, change.count());
        assertEquals(Blocks.DIRT.defaultBlockState(), container.get(0, 0, 0));
    }
}
