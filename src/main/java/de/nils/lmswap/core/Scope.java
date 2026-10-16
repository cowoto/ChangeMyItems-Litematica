package de.nils.lmswap.core;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;

/**
 * Where a rule applies.
 * <ul>
 *   <li>{@link WholeSchematic}: all regions (the behaviour before scopes existed)</li>
 *   <li>{@link Regions}: only the named sub-regions</li>
 *   <li>{@link WorldBoxes}: only blocks inside the boxes, in world coordinates. Needs a placement to be converted to
 *       region coordinates, see {@link ScopeResolver}.</li>
 * </ul>
 */
public sealed interface Scope permits Scope.WholeSchematic, Scope.Regions, Scope.WorldBoxes
{
    WholeSchematic WHOLE = new WholeSchematic();

    record WholeSchematic() implements Scope
    {
    }

    record Regions(Set<String> names) implements Scope
    {
        public Regions
        {
            names = Set.copyOf(names);
        }
    }

    /**
     * @param boxes inclusive boxes in world coordinates
     */
    record WorldBoxes(List<WorldBox> boxes) implements Scope
    {
        public WorldBoxes
        {
            boxes = List.copyOf(boxes);
        }
    }

    /**
     * Inclusive box in world coordinates; the corners can be given in any order.
     */
    record WorldBox(BlockPos min, BlockPos max)
    {
        public static WorldBox of(BlockPos a, BlockPos b)
        {
            return new WorldBox(new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
        }
    }
}
