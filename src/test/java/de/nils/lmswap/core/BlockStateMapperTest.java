package de.nils.lmswap.core;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStateMapperTest
{
    @BeforeAll
    static void bootstrap()
    {
        MinecraftTestBootstrap.init();
    }

    @Test
    void slabTypeTopIsKept()
    {
        BlockState source = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);

        BlockStateMapper.MappingResult result = BlockStateMapper.map(source, Blocks.STONE_SLAB);

        assertEquals(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), result.state());
        assertTrue(result.isComplete());
    }

    @Test
    void slabWaterloggedIsKept()
    {
        BlockState source = Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                .setValue(SlabBlock.WATERLOGGED, true);

        BlockStateMapper.MappingResult result = BlockStateMapper.map(source, Blocks.OAK_SLAB);

        assertEquals(SlabType.BOTTOM, result.state().getValue(SlabBlock.TYPE));
        assertTrue(result.state().getValue(SlabBlock.WATERLOGGED));
        assertTrue(result.isComplete());
    }

    @Test
    void stairsKeepFacingHalfShape()
    {
        BlockState source = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST)
                .setValue(StairBlock.HALF, Half.TOP)
                .setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT);

        BlockStateMapper.MappingResult result = BlockStateMapper.map(source, Blocks.STONE_BRICK_STAIRS);

        assertEquals(Blocks.STONE_BRICK_STAIRS, result.state().getBlock());
        assertEquals(Direction.EAST, result.state().getValue(StairBlock.FACING));
        assertEquals(Half.TOP, result.state().getValue(StairBlock.HALF));
        assertEquals(StairsShape.OUTER_LEFT, result.state().getValue(StairBlock.SHAPE));
        assertTrue(result.isComplete());
    }

    @Test
    void slabToFullBlockDropsPropertiesWithWarning()
    {
        BlockState source = Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP)
                .setValue(SlabBlock.WATERLOGGED, true);

        BlockStateMapper.MappingResult result = BlockStateMapper.map(source, Blocks.STONE);

        assertEquals(Blocks.STONE.defaultBlockState(), result.state());
        assertFalse(result.isComplete());
        assertEquals(List.of("type", "waterlogged"), result.droppedProperties().stream().sorted().toList());
    }

    @Test
    void invalidValueIsDropped()
    {
        // facing=up exists on the observer, but stairs only allow horizontal directions
        BlockState source = Blocks.OBSERVER.defaultBlockState().setValue(net.minecraft.world.level.block.ObserverBlock.FACING, Direction.UP);

        BlockStateMapper.MappingResult result = BlockStateMapper.map(source, Blocks.OAK_STAIRS);

        assertEquals(Blocks.OAK_STAIRS.defaultBlockState().getValue(StairBlock.FACING), result.state().getValue(StairBlock.FACING));
        assertTrue(result.droppedProperties().contains("facing"));
    }

    @Test
    void sameProperties()
    {
        assertTrue(BlockStateMapper.haveSameProperties(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB));
        assertTrue(BlockStateMapper.haveSameProperties(Blocks.OAK_STAIRS, Blocks.STONE_BRICK_STAIRS));
        assertFalse(BlockStateMapper.haveSameProperties(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE));
        assertFalse(BlockStateMapper.haveSameProperties(Blocks.OAK_STAIRS, Blocks.OAK_SLAB));
    }
}
