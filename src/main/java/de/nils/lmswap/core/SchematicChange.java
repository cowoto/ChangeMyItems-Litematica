package de.nils.lmswap.core;

import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import fi.dy.masa.malilib.util.data.tag.CompoundData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.ScheduledTick;

/**
 * Everything needed to undo one replacement: per region the container indices grouped by their old state,
 * plus removed block entity data and scheduled block ticks.
 */
public record SchematicChange(List<RegionChange> regions, int totalBlocksDelta)
{
    public record RegionChange(String regionName,
                               Map<BlockState, LongArrayList> oldStates,
                               Map<BlockPos, CompoundData> removedBlockEntities,
                               Map<BlockPos, ScheduledTick<Block>> removedBlockTicks)
    {
    }
}
