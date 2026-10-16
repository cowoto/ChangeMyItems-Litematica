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
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.ScheduledTick;

import de.nils.lmswap.core.ReplacementResult.Message;
import de.nils.lmswap.core.ResolvedScope.RegionPart;
import de.nils.lmswap.core.SchematicChange.RegionChange;

/**
 * Replaces blocks in all regions of a loaded schematic, in memory only.
 * <p>
 * Strategy (see docs/DECISIONS.md): the palette is used to find out which palette ids match which rules (state part:
 * block and property conditions) and to pre-register the target states; the storage is then scanned once comparing
 * raw ids. Rules with a scope additionally check the position. For each position the first matching rule in list
 * order wins, so every position is replaced at most once per pass. The palette itself is never rewritten, so each
 * changed position is known (needed for undo and block entities).
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
        return apply(schematic, List.of(rule), null);
    }

    public static ReplacementResult apply(LitematicaSchematic schematic, List<ReplacementRule> rules)
    {
        return apply(schematic, rules, null);
    }

    /**
     * Applies several rules in one pass. For each position the first matching rule wins. The result holds a single
     * undo record.
     *
     * @param placement needed to resolve {@link Scope.WorldBoxes} scopes, may be null otherwise
     */
    public static ReplacementResult apply(LitematicaSchematic schematic, List<ReplacementRule> rules,
                                          @Nullable SchematicPlacement placement)
    {
        Message error = check(rules);

        if (error != null)
        {
            return ReplacementResult.rejected(error);
        }

        ResolvedScope[] scopes = resolveScopes(schematic, rules, placement);

        if (scopes == null)
        {
            return ReplacementResult.rejected(Message.of("lmswap.error.scope_unresolvable"));
        }

        return applyResolved(schematic, rules, scopes);
    }

    /**
     * Like {@link #apply(LitematicaSchematic, List, SchematicPlacement)} with the scopes already converted to region
     * coordinates (one per rule). Rules are assumed to be checked.
     */
    static ReplacementResult applyResolved(LitematicaSchematic schematic, List<ReplacementRule> rules, ResolvedScope[] scopes)
    {
        Map<String, Integer> replacedPerRegion = new LinkedHashMap<>();
        Set<Message> warnings = new LinkedHashSet<>();
        List<RegionChange> regionChanges = new ArrayList<>();
        int totalBlocksDelta = 0;

        for (String regionName : schematic.getAreas().keySet())
        {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);
            RegionPart[] parts = partsFor(scopes, regionName);

            if (container == null || parts == null)
            {
                continue;
            }

            Map<BlockPos, CompoundData> blockEntities = schematic.getBlockEntityMapForRegion(regionName);
            Map<BlockPos, ScheduledTick<Block>> blockTicks = schematic.getScheduledBlockTicksForRegion(regionName);
            Compiled compiled = Compiled.of(container, rules, parts, warnings);

            // Positions with extra data that are about to be replaced; usually a tiny set compared to the volume
            List<BlockPos> blockEntityCandidates = compiled.matchingPositions(container, blockEntities);
            List<BlockPos> blockTickCandidates = compiled.matchingPositions(container, blockTicks);

            ContainerChange change = replace(container, compiled);

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
     * Restores the state from before the replacement described by {@code change}. Region changes are reverted in
     * reverse order, so a change made of several passes over the same positions is undone correctly.
     */
    public static void revert(LitematicaSchematic schematic, SchematicChange change)
    {
        List<RegionChange> regions = change.regions();

        for (int i = regions.size() - 1; i >= 0; --i)
        {
            RegionChange region = regions.get(i);
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
     * Counts the blocks in all regions that the rules would replace.
     */
    public static int countMatching(LitematicaSchematic schematic, List<ReplacementRule> rules)
    {
        return countMatching(schematic, rules, null);
    }

    /**
     * @return the number of blocks the rules would replace, or -1 if a scope can not be resolved
     */
    public static int countMatching(LitematicaSchematic schematic, List<ReplacementRule> rules, @Nullable SchematicPlacement placement)
    {
        ResolvedScope[] scopes = resolveScopes(schematic, rules, placement);

        if (scopes == null)
        {
            return -1;
        }

        int count = 0;

        for (String regionName : schematic.getAreas().keySet())
        {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);
            RegionPart[] parts = partsFor(scopes, regionName);

            if (container != null && parts != null)
            {
                count += count(container, Compiled.of(container, rules, parts, null));
            }
        }

        return count;
    }

    /**
     * Blocks per state over all regions (air excluded), e.g. to list the materials of a schematic.
     */
    public static Map<BlockState, Integer> countAllStates(LitematicaSchematic schematic)
    {
        Map<BlockState, Integer> counts = new HashMap<>();

        for (String regionName : schematic.getAreas().keySet())
        {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);

            if (container == null)
            {
                continue;
            }

            ILitematicaBlockStatePalette palette = container.getPalette();
            final int paletteSize = palette.getPaletteSize();
            int[] perId = new int[paletteSize];
            LitematicaBitArray storage = container.getArray();
            Vec3i size = container.getSize();
            final long volume = (long) size.getX() * size.getY() * size.getZ();

            for (long index = 0; index < volume; ++index)
            {
                int id = storage.getAt(index);

                if (id < paletteSize)
                {
                    ++perId[id];
                }
            }

            for (int id = 0; id < paletteSize; ++id)
            {
                BlockState state = palette.getBlockState(id);

                if (perId[id] > 0 && state != null && state.isAir() == false)
                {
                    counts.merge(state, perId[id], Integer::sum);
                }
            }
        }

        return counts;
    }

    static int countMatching(LitematicaBlockStateContainer container, List<ReplacementRule> rules)
    {
        return count(container, Compiled.of(container, rules, wholeParts(rules.size()), null));
    }

    /**
     * Like {@link #countMatching} but per original block state, for live previews of filters: once counted, any
     * filter combination is just a sum over this map.
     *
     * @return empty if a scope can not be resolved
     */
    public static Map<BlockState, Integer> countPerState(LitematicaSchematic schematic, List<ReplacementRule> rules,
                                                         @Nullable SchematicPlacement placement)
    {
        ResolvedScope[] scopes = resolveScopes(schematic, rules, placement);
        Map<BlockState, Integer> counts = new HashMap<>();

        if (scopes == null)
        {
            return counts;
        }

        for (String regionName : schematic.getAreas().keySet())
        {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);
            RegionPart[] parts = partsFor(scopes, regionName);

            if (container == null || parts == null)
            {
                continue;
            }

            Compiled compiled = Compiled.of(container, rules, parts, null);
            int[] perId = new int[compiled.paletteSize];
            count(container, compiled, perId);

            for (int id = 0; id < perId.length; ++id)
            {
                if (perId[id] > 0)
                {
                    counts.merge(compiled.oldStates[id], perId[id], Integer::sum);
                }
            }
        }

        return counts;
    }

    static ContainerChange replaceInContainer(LitematicaBlockStateContainer container, ReplacementRule rule, Set<Message> warnings)
    {
        return replaceInContainer(container, List.of(rule), warnings);
    }

    static ContainerChange replaceInContainer(LitematicaBlockStateContainer container, List<ReplacementRule> rules, Set<Message> warnings)
    {
        return replace(container, Compiled.of(container, rules, wholeParts(rules.size()), warnings));
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

    @Nullable
    private static Message check(List<ReplacementRule> rules)
    {
        for (ReplacementRule rule : rules)
        {
            List<Message> errors = rule.validate();

            if (errors.isEmpty() == false)
            {
                return errors.getFirst();
            }

            if (rule.isPropertyCompatible() == false)
            {
                return Message.of("lmswap.error.properties_differ",
                                  BlockStateMapper.blockId(rule.sourceBlock()), BlockStateMapper.blockId(rule.target()));
            }
        }

        return null;
    }

    @Nullable
    private static ResolvedScope[] resolveScopes(LitematicaSchematic schematic, List<ReplacementRule> rules,
                                                 @Nullable SchematicPlacement placement)
    {
        ResolvedScope[] scopes = new ResolvedScope[rules.size()];

        for (int i = 0; i < scopes.length; ++i)
        {
            scopes[i] = ScopeResolver.resolve(rules.get(i).scope(), schematic, placement);

            if (scopes[i] == null)
            {
                return null;
            }
        }

        return scopes;
    }

    /**
     * @return per rule its part of this region (null entries = rule inactive here), or null if no rule touches it
     */
    @Nullable
    private static RegionPart[] partsFor(ResolvedScope[] scopes, String regionName)
    {
        RegionPart[] parts = new RegionPart[scopes.length];
        boolean any = false;

        for (int i = 0; i < scopes.length; ++i)
        {
            parts[i] = scopes[i].forRegion(regionName);
            any |= parts[i] != null;
        }

        return any ? parts : null;
    }

    private static RegionPart[] wholeParts(int count)
    {
        RegionPart[] parts = new RegionPart[count];
        java.util.Arrays.fill(parts, RegionPart.WHOLE);
        return parts;
    }

    private static int count(LitematicaBlockStateContainer container, Compiled compiled)
    {
        return count(container, compiled, null);
    }

    /**
     * @param perId if not null, receives the count per palette id
     */
    private static int count(LitematicaBlockStateContainer container, Compiled compiled, @Nullable int[] perId)
    {
        if (compiled.anyMatch == false)
        {
            return 0;
        }

        LitematicaBitArray storage = container.getArray();
        Vec3i size = container.getSize();
        int count = 0;
        long index = 0;

        for (int y = 0; y < size.getY(); ++y)
        {
            for (int z = 0; z < size.getZ(); ++z)
            {
                for (int x = 0; x < size.getX(); ++x, ++index)
                {
                    int id = storage.getAt(index);

                    if (compiled.candidateAt(id, x, y, z) >= 0)
                    {
                        ++count;

                        if (perId != null)
                        {
                            ++perId[id];
                        }
                    }
                }
            }
        }

        return count;
    }

    private static ContainerChange replace(LitematicaBlockStateContainer container, Compiled compiled)
    {
        if (compiled.anyMatch == false)
        {
            return ContainerChange.EMPTY;
        }

        // Registering new states may resize the palette and replace the storage. Existing ids stay stable
        // (Litematica's resize copies raw ids), so look up the new ids only after all states are registered.
        for (BlockState[] states : compiled.newStates)
        {
            if (states != null)
            {
                for (BlockState state : states)
                {
                    container.getPalette().idFor(state);
                }
            }
        }

        int[][] newIds = new int[compiled.paletteSize][];

        for (int id = 0; id < compiled.paletteSize; ++id)
        {
            BlockState[] states = compiled.newStates[id];

            if (states != null)
            {
                newIds[id] = new int[states.length];

                for (int k = 0; k < states.length; ++k)
                {
                    newIds[id][k] = container.getPalette().idFor(states[k]);
                }
            }
        }

        LitematicaBitArray storage = container.getArray();
        Vec3i size = container.getSize();
        final long volume = (long) size.getX() * size.getY() * size.getZ();

        if (volume > Integer.MAX_VALUE)
        {
            throw new IllegalStateException("Region too large for undo indices: " + volume);
        }

        Map<BlockState, IntArrayList> changed = new Object2ObjectOpenHashMap<>();
        int count = 0;
        int totalBlocksDelta = 0;
        int index = 0;

        for (int y = 0; y < size.getY(); ++y)
        {
            for (int z = 0; z < size.getZ(); ++z)
            {
                for (int x = 0; x < size.getX(); ++x, ++index)
                {
                    int id = storage.getAt(index);
                    int k = compiled.candidateAt(id, x, y, z);

                    if (k >= 0)
                    {
                        BlockState oldState = compiled.oldStates[id];
                        BlockState newState = compiled.newStates[id][k];
                        storage.setAt(index, newIds[id][k]);
                        changed.computeIfAbsent(oldState, s -> new IntArrayList()).add(index);
                        ++count;
                        totalBlocksDelta += airDelta(oldState, newState);
                    }
                }
            }
        }

        return new ContainerChange(count, totalBlocksDelta, changed);
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

    /**
     * The rules of one pass, prepared for one region container: per palette id the candidate rules (in list order)
     * whose state part matches, with the mapped target state of each.
     */
    private static final class Compiled
    {
        final int paletteSize;
        final BlockState[] oldStates;
        /** per palette id: candidate rule parts, null = no candidate */
        final RegionPart[][] candidateParts;
        /** per palette id: mapped state per candidate; null entries where mapping gives the same state */
        final BlockState[][] newStates;
        final boolean anyMatch;

        private Compiled(int paletteSize, BlockState[] oldStates, RegionPart[][] candidateParts, BlockState[][] newStates, boolean anyMatch)
        {
            this.paletteSize = paletteSize;
            this.oldStates = oldStates;
            this.candidateParts = candidateParts;
            this.newStates = newStates;
            this.anyMatch = anyMatch;
        }

        static Compiled of(LitematicaBlockStateContainer container, List<ReplacementRule> rules, RegionPart[] parts,
                           @Nullable Set<Message> warnings)
        {
            ILitematicaBlockStatePalette palette = container.getPalette();
            final int paletteSize = palette.getPaletteSize();
            BlockState[] oldStates = new BlockState[paletteSize];
            RegionPart[][] candidateParts = new RegionPart[paletteSize][];
            BlockState[][] newStates = new BlockState[paletteSize][];
            boolean anyMatch = false;

            for (int id = 0; id < paletteSize; ++id)
            {
                BlockState state = palette.getBlockState(id);

                if (state == null)
                {
                    continue;
                }

                List<RegionPart> candParts = new ArrayList<>(1);
                List<BlockState> candStates = new ArrayList<>(1);

                for (int r = 0; r < rules.size(); ++r)
                {
                    ReplacementRule rule = rules.get(r);

                    if (parts[r] == null || rule.matches(state) == false)
                    {
                        continue;
                    }

                    BlockStateMapper.MappingResult mapped = rule.map(state);

                    // A rule that maps the state to itself still "wins" the position (first match), it just does nothing
                    candParts.add(parts[r]);
                    candStates.add(mapped.state());

                    if (warnings != null && mapped.state() != state && mapped.isComplete() == false)
                    {
                        warnings.add(Message.of("lmswap.warning.properties_dropped", state, String.join(", ", mapped.droppedProperties())));
                    }

                    if (parts[r].isWhole())
                    {
                        break; // later rules can never win for this id
                    }
                }

                if (candParts.isEmpty() == false)
                {
                    oldStates[id] = state;
                    candidateParts[id] = candParts.toArray(new RegionPart[0]);
                    newStates[id] = candStates.toArray(new BlockState[0]);
                    anyMatch |= candStates.stream().anyMatch(s -> s != state);
                }
            }

            return new Compiled(paletteSize, oldStates, candidateParts, newStates, anyMatch);
        }

        /**
         * @return index of the winning candidate for this id and position, -1 if none or if it maps to the same state
         */
        int candidateAt(int id, int x, int y, int z)
        {
            if (id >= this.paletteSize)
            {
                return -1;
            }

            RegionPart[] parts = this.candidateParts[id];

            if (parts == null)
            {
                return -1;
            }

            for (int k = 0; k < parts.length; ++k)
            {
                if (parts[k].contains(x, y, z))
                {
                    return this.newStates[id][k] != this.oldStates[id] ? k : -1;
                }
            }

            return -1;
        }

        <T> List<BlockPos> matchingPositions(LitematicaBlockStateContainer container, @Nullable Map<BlockPos, T> map)
        {
            List<BlockPos> list = new ArrayList<>();

            if (map != null && this.anyMatch)
            {
                LitematicaBitArray storage = container.getArray();
                Vec3i size = container.getSize();

                for (BlockPos pos : map.keySet())
                {
                    long index = ((long) pos.getY() * size.getZ() + pos.getZ()) * size.getX() + pos.getX();

                    if (this.candidateAt(storage.getAt(index), pos.getX(), pos.getY(), pos.getZ()) >= 0)
                    {
                        list.add(pos);
                    }
                }
            }

            return list;
        }
    }

    record ContainerChange(int count, int totalBlocksDelta, Map<BlockState, IntArrayList> oldStates)
    {
        static final ContainerChange EMPTY = new ContainerChange(0, 0, Map.of());
    }
}
