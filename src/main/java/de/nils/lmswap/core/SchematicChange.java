package de.nils.lmswap.core;

import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import fi.dy.masa.malilib.util.data.tag.CompoundData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.ScheduledTick;

/**
 * Everything needed to undo one replacement: per region the container indices grouped by their old state,
 * plus removed block entity data and scheduled block ticks.
 * <p>
 * Indices are stored as int (4 bytes per changed block); see docs/DECISIONS.md for why this beats a palette or
 * storage snapshot.
 */
public record SchematicChange(List<RegionChange> regions, int totalBlocksDelta)
{
    /** Number of recorded positions, i.e. the memory cost of this undo entry (4 bytes each) */
    public long changedPositions()
    {
        long count = 0;

        for (RegionChange region : this.regions)
        {
            for (IntArrayList indices : region.oldStates().values())
            {
                count += indices.size();
            }
        }

        return count;
    }

    public record RegionChange(String regionName,
                               Map<BlockState, IntArrayList> oldStates,
                               Map<BlockPos, CompoundData> removedBlockEntities,
                               Map<BlockPos, ScheduledTick<Block>> removedBlockTicks)
    {
    }
}
