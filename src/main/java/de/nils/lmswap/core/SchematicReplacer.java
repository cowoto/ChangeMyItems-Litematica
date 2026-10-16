package de.nils.lmswap.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.container.ILitematicaBlockStatePalette;
import fi.dy.masa.litematica.schematic.container.LitematicaBitArray;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.ScheduledTick;

import de.nils.lmswap.core.ReplacementResult.Message;
import de.nils.lmswap.core.SchematicChange.RegionChange;

/**
 * Replaces blocks in all regions of a loaded schematic, in memory only.
 * <p>
 * Strategy (see docs/DECISIONS.md): the palette is used to find out which palette ids match the rule and to
 * pre-register the target states; the storage is then scanned once comparing raw ids. The palette itself is
 * never rewritten, so each changed position is known (needed for undo and block entities).
 * <p>
 * Does not trigger a re-render; callers must mark the placements for rebuild.
 */
public final class SchematicReplacer
{
    private SchematicReplacer()
    {
    }

    public static ReplacementResult apply(LitematicaSchematic schematic, ReplacementRule rule)
    {
        return apply(schematic, List.of(rule));
    }

    /**
     * Applies several rules in one pass, e.g. one per block state behind a material list entry.
     * For each state the first matching rule wins. The result holds a single undo record.
     */
    public static ReplacementResult apply(LitematicaSchematic schematic, List<ReplacementRule> rules)
    {
        for (ReplacementRule rule : rules)
        {
            if (rule.isPropertyCompatible() == false)
            {
                return ReplacementResult.rejected(Message.of("lmswap.error.properties_differ",
                                                             blockId(rule.sourceBlock()), blockId(rule.target())));
            }
        }

        Map<String, Integer> replacedPerRegion = new LinkedHashMap<>();
        Set<Message> warnings = new LinkedHashSet<>();
        List<RegionChange> regionChanges = new ArrayList<>();
        int totalBlocksDelta = 0;

        for (String regionName : schematic.getAreas().keySet())
        {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);

            if (container == null)
            {
                continue;
            }

            Map<BlockPos, CompoundData> blockEntities = schematic.getBlockEntityMapForRegion(regionName);
            Map<BlockPos, ScheduledTick<Block>> blockTicks = schematic.getScheduledBlockTicksForRegion(regionName);

            // Positions with extra data that are about to be replaced; usually a tiny set compared to the volume
            List<BlockPos> blockEntityCandidates = matchingPositions(container, blockEntities, rules);
            List<BlockPos> blockTickCandidates = matchingPositions(container, blockTicks, rules);

            ContainerChange change = replaceInContainer(container, rules, warnings);

            if (change.count() == 0)
            {
                continue;
            }

            Map<BlockPos, CompoundData> removedBlockEntities = new HashMap<>();
            int keptBlockEntities = 0;

            for (BlockPos pos : blockEntityCandidates)
            {
                if (container.get(pos.getX(), pos.getY(), pos.getZ()).hasBlockEntity())
                {
                    ++keptBlockEntities;
                }
                else
                {
                    removedBlockEntities.put(pos, blockEntities.remove(pos));
                }
            }

            Map<BlockPos, ScheduledTick<Block>> removedBlockTicks = new HashMap<>();

            for (BlockPos pos : blockTickCandidates)
            {
                if (blockTicks.get(pos).type() != container.get(pos.getX(), pos.getY(), pos.getZ()).getBlock())
                {
                    removedBlockTicks.put(pos, blockTicks.remove(pos));
                }
            }

            if (removedBlockEntities.isEmpty() == false)
            {
                warnings.add(Message.of("lmswap.warning.block_entities_removed", removedBlockEntities.size(), regionName));
            }

            if (keptBlockEntities > 0)
            {
                warnings.add(Message.of("lmswap.warning.block_entities_kept", keptBlockEntities, regionName));
            }

            replacedPerRegion.put(regionName, change.count());
            regionChanges.add(new RegionChange(regionName, change.oldStates(), removedBlockEntities, removedBlockTicks));
            totalBlocksDelta += change.totalBlocksDelta();
        }

        if (regionChanges.isEmpty())
        {
            return new ReplacementResult(Map.of(), List.copyOf(warnings), null, null);
        }

        SchematicChange change = new SchematicChange(List.copyOf(regionChanges), totalBlocksDelta);
        updateMetadata(schematic, totalBlocksDelta);

        return new ReplacementResult(replacedPerRegion, List.copyOf(warnings), null, change);
    }

    /**
     * Restores the state from before the replacement described by {@code change}.
     */
    public static void revert(LitematicaSchematic schematic, SchematicChange change)
    {
        for (RegionChange region : change.regions())
        {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(region.regionName());

            if (container == null)
            {
                continue;
            }

            revertContainer(container, region.oldStates());

            Map<BlockPos, CompoundData> blockEntities = schematic.getBlockEntityMapForRegion(region.regionName());
            Map<BlockPos, ScheduledTick<Block>> blockTicks = schematic.getScheduledBlockTicksForRegion(region.regionName());

            if (blockEntities != null)
            {
                blockEntities.putAll(region.removedBlockEntities());
            }

            if (blockTicks != null)
            {
                blockTicks.putAll(region.removedBlockTicks());
            }
        }

        updateMetadata(schematic, -change.totalBlocksDelta());
    }

    /**
     * Counts the blocks in all regions that one of the rules would replace.
     */
    public static int countMatching(LitematicaSchematic schematic, List<ReplacementRule> rules)
    {
        int count = 0;

        for (String regionName : schematic.getAreas().keySet())
        {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);

            if (container != null)
            {
                count += countMatching(container, rules);
            }
        }

        return count;
    }

    static int countMatching(LitematicaBlockStateContainer container, List<ReplacementRule> rules)
    {
        ILitematicaBlockStatePalette palette = container.getPalette();
        final int paletteSize = palette.getPaletteSize();
        boolean[] matches = new boolean[paletteSize];
        boolean anyMatch = false;

        for (int id = 0; id < paletteSize; ++id)
        {
            BlockState state = palette.getBlockState(id);

            if (state != null && ruleFor(rules, state) != null)
            {
                matches[id] = true;
                anyMatch = true;
            }
        }

        if (anyMatch == false)
        {
            return 0;
        }

        LitematicaBitArray storage = container.getArray();
        Vec3i size = container.getSize();
        final long volume = (long) size.getX() * size.getY() * size.getZ();
        int count = 0;

        for (long index = 0; index < volume; ++index)
        {
            int id = storage.getAt(index);

            if (id < paletteSize && matches[id])
            {
                ++count;
            }
        }

        return count;
    }

    static ContainerChange replaceInContainer(LitematicaBlockStateContainer container, ReplacementRule rule, Set<Message> warnings)
    {
        return replaceInContainer(container, List.of(rule), warnings);
    }

    static ContainerChange replaceInContainer(LitematicaBlockStateContainer container, List<ReplacementRule> rules, Set<Message> warnings)
    {
        ILitematicaBlockStatePalette palette = container.getPalette();
        final int paletteSize = palette.getPaletteSize();
        BlockState[] oldStates = new BlockState[paletteSize];
        BlockState[] newStates = new BlockState[paletteSize];
        boolean anyMatch = false;

        for (int id = 0; id < paletteSize; ++id)
        {
            BlockState state = palette.getBlockState(id);
            ReplacementRule rule = state != null ? ruleFor(rules, state) : null;

            if (rule != null)
            {
                BlockStateMapper.MappingResult mapped = rule.map(state);

                if (mapped.state() != state)
                {
                    oldStates[id] = state;
                    newStates[id] = mapped.state();
                    anyMatch = true;

                    if (mapped.isComplete() == false)
                    {
                        warnings.add(Message.of("lmswap.warning.properties_dropped",
                                                state, String.join(", ", mapped.droppedProperties())));
                    }
                }
            }
        }

        if (anyMatch == false)
        {
            return ContainerChange.EMPTY;
        }

        // Registering new states may resize the palette and replace the storage. Existing ids stay stable
        // (Litematica's resize copies raw ids), so look up the new ids only after all states are registered.
        for (BlockState state : newStates)
        {
            if (state != null)
            {
                container.getPalette().idFor(state);
            }
        }

        int[] newIds = new int[paletteSize];

        for (int id = 0; id < paletteSize; ++id)
        {
            newIds[id] = newStates[id] != null ? container.getPalette().idFor(newStates[id]) : -1;
        }

        LitematicaBitArray storage = container.getArray();
        Vec3i size = container.getSize();
        final long volume = (long) size.getX() * size.getY() * size.getZ();
        Map<BlockState, IntArrayList> changed = new Object2ObjectOpenHashMap<>();

        if (volume > Integer.MAX_VALUE)
        {
            throw new IllegalStateException("Region too large for undo indices: " + volume);
        }
        int count = 0;
        int totalBlocksDelta = 0;

        for (long index = 0; index < volume; ++index)
        {
            int id = storage.getAt(index);

            if (id < paletteSize && newIds[id] >= 0)
            {
                storage.setAt(index, newIds[id]);
                changed.computeIfAbsent(oldStates[id], k -> new IntArrayList()).add((int) index);
                ++count;
                totalBlocksDelta += airDelta(oldStates[id], newStates[id]);
            }
        }

        return new ContainerChange(count, totalBlocksDelta, changed);
    }

    static void revertContainer(LitematicaBlockStateContainer container, Map<BlockState, IntArrayList> oldStates)
    {
        Vec3i size = container.getSize();
        final int sizeX = size.getX();
        final int sizeZ = size.getZ();
        final long sizeLayer = (long) sizeX * sizeZ;

        for (Map.Entry<BlockState, IntArrayList> entry : oldStates.entrySet())
        {
            BlockState state = entry.getKey();
            IntArrayList indices = entry.getValue();

            for (int i = 0; i < indices.size(); ++i)
            {
                long index = indices.getInt(i);
                int y = (int) (index / sizeLayer);
                int rest = (int) (index % sizeLayer);
                container.set(rest % sizeX, y, rest / sizeX, state);
            }
        }
    }

    private static <T> List<BlockPos> matchingPositions(LitematicaBlockStateContainer container, Map<BlockPos, T> map, List<ReplacementRule> rules)
    {
        List<BlockPos> list = new ArrayList<>();

        if (map != null)
        {
            for (BlockPos pos : map.keySet())
            {
                if (ruleFor(rules, container.get(pos.getX(), pos.getY(), pos.getZ())) != null)
                {
                    list.add(pos);
                }
            }
        }

        return list;
    }

    @Nullable
    private static ReplacementRule ruleFor(List<ReplacementRule> rules, BlockState state)
    {
        for (ReplacementRule rule : rules)
        {
            if (rule.matches(state))
            {
                return rule;
            }
        }

        return null;
    }

    private static int airDelta(BlockState oldState, BlockState newState)
    {
        return (newState.isAir() ? 0 : 1) - (oldState.isAir() ? 0 : 1);
    }

    private static void updateMetadata(LitematicaSchematic schematic, int totalBlocksDelta)
    {
        SchematicMetadata metadata = schematic.getMetadata();
        metadata.setTotalBlocks(metadata.getTotalBlocks() + totalBlocksDelta);
        metadata.setTimeModifiedToNow();
        metadata.setModifiedSinceSaved();
    }

    private static String blockId(Block block)
    {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    record ContainerChange(int count, int totalBlocksDelta, Map<BlockState, IntArrayList> oldStates)
    {
        static final ContainerChange EMPTY = new ContainerChange(0, 0, Map.of());
    }
}
