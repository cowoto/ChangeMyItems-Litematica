package de.nils.lmswap.core;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.util.PositionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Converts between container coordinates of one schematic region (0..size-1, as used by
 * LitematicaBlockStateContainer) and world coordinates of a placement.
 * <p>
 * The forward direction uses Litematica's own primitives in the same order as Litematica's
 * {@code WorldPlacingUtils.placeBlocksToProtoChunk}: offset to the region's minimum corner, placement mirror and
 * rotation, sub-region mirror and rotation, then the transformed sub-region position and the placement origin.
 * All of these are 90° rotations and mirrors without pivot, so the transform is {@code world = A * local + b} with
 * a signed permutation matrix A. The inverse is therefore exact: {@code local = Aᵀ * (world - b)}. A and b are read
 * off the forward transform, so both directions always agree with Litematica.
 */
public final class RegionTransform
{
    /** a[row][col] of the signed permutation matrix */
    private final int[][] a;
    private final int[] b;

    private RegionTransform(int[][] a, int[] b)
    {
        this.a = a;
        this.b = b;
    }

    /**
     * @param origin     placement origin
     * @param regionPos  sub-region position relative to the placement origin (SubRegionPlacement.getPos())
     * @param regionSize sub-region size as stored in the schematic (components may be negative)
     */
    public static RegionTransform of(BlockPos origin, Mirror mirror, Rotation rotation,
                                     BlockPos regionPos, Vec3i regionSize, Mirror subMirror, Rotation subRotation)
    {
        Forward forward = new Forward(origin, mirror, rotation, regionPos, regionSize, subMirror, subRotation);
        BlockPos zero = forward.apply(0, 0, 0);
        int[] b = { zero.getX(), zero.getY(), zero.getZ() };
        int[][] a = new int[3][3];
        BlockPos[] columns = { forward.apply(1, 0, 0), forward.apply(0, 1, 0), forward.apply(0, 0, 1) };

        for (int col = 0; col < 3; ++col)
        {
            a[0][col] = columns[col].getX() - b[0];
            a[1][col] = columns[col].getY() - b[1];
            a[2][col] = columns[col].getZ() - b[2];
        }

        return new RegionTransform(a, b);
    }

    /**
     * @return null if the region does not exist in the placement or schematic
     */
    @Nullable
    public static RegionTransform of(SchematicPlacement placement, String regionName)
    {
        SubRegionPlacement sub = placement.getRelativeSubRegionPlacement(regionName);
        Vec3i size = placement.getSchematic().getAreaSizeAsVec3i(regionName);

        if (sub == null || size == null)
        {
            return null;
        }

        return of(placement.getOrigin(), placement.getMirror(), placement.getRotation(), sub.getPos(), size, sub.getMirror(), sub.getRotation());
    }

    public BlockPos toWorld(int x, int y, int z)
    {
        return new BlockPos(this.a[0][0] * x + this.a[0][1] * y + this.a[0][2] * z + this.b[0],
                            this.a[1][0] * x + this.a[1][1] * y + this.a[1][2] * z + this.b[1],
                            this.a[2][0] * x + this.a[2][1] * y + this.a[2][2] * z + this.b[2]);
    }

    public BlockPos toLocal(BlockPos world)
    {
        int dx = world.getX() - this.b[0];
        int dy = world.getY() - this.b[1];
        int dz = world.getZ() - this.b[2];

        // Inverse of a signed permutation matrix is its transpose
        return new BlockPos(this.a[0][0] * dx + this.a[1][0] * dy + this.a[2][0] * dz,
                            this.a[0][1] * dx + this.a[1][1] * dy + this.a[2][1] * dz,
                            this.a[0][2] * dx + this.a[1][2] * dy + this.a[2][2] * dz);
    }

    /**
     * The part of a world box that lies inside the region, as a container box.
     *
     * @param containerSize size of the region's container (all components positive)
     * @return null if the box does not intersect the region
     */
    @Nullable
    public ResolvedScope.LocalBox toLocalBox(Scope.WorldBox box, Vec3i containerSize)
    {
        BlockPos p1 = this.toLocal(box.min());
        BlockPos p2 = this.toLocal(box.max());
        int minX = Math.max(0, Math.min(p1.getX(), p2.getX()));
        int minY = Math.max(0, Math.min(p1.getY(), p2.getY()));
        int minZ = Math.max(0, Math.min(p1.getZ(), p2.getZ()));
        int maxX = Math.min(containerSize.getX() - 1, Math.max(p1.getX(), p2.getX()));
        int maxY = Math.min(containerSize.getY() - 1, Math.max(p1.getY(), p2.getY()));
        int maxZ = Math.min(containerSize.getZ() - 1, Math.max(p1.getZ(), p2.getZ()));

        if (minX > maxX || minY > maxY || minZ > maxZ)
        {
            return null;
        }

        return new ResolvedScope.LocalBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Litematica's forward position transform, see the class comment.
     */
    private record Forward(BlockPos origin, Mirror mirror, Rotation rotation, BlockPos regionPos, Vec3i regionSize,
                           Mirror subMirror, Rotation subRotation)
    {
        BlockPos apply(int x, int y, int z)
        {
            BlockPos posEndRel = PositionUtils.getRelativeEndPositionFromAreaSize(this.regionSize).offset(this.regionPos);
            BlockPos posMinRel = PositionUtils.getMinCorner(this.regionPos, posEndRel);
            BlockPos regionPosTransformed = PositionUtils.getTransformedBlockPos(this.regionPos, this.mirror, this.rotation);

            BlockPos rel = new BlockPos(posMinRel.getX() - this.regionPos.getX() + x,
                                        posMinRel.getY() - this.regionPos.getY() + y,
                                        posMinRel.getZ() - this.regionPos.getZ() + z);
            rel = PositionUtils.getTransformedBlockPos(rel, this.mirror, this.rotation);
            rel = PositionUtils.getTransformedBlockPos(rel, this.subMirror, this.subRotation);

            return rel.offset(regionPosTransformed).offset(this.origin);
        }
    }
}
