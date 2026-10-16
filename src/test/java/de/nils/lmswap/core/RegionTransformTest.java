package de.nils.lmswap.core;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Part B: world <-> region coordinates for every rotation and mirror of placement and sub-region.
 * Expected values in the hand-checked tests follow Litematica's/vanilla's convention: CLOCKWISE_90 turns +X into +Z,
 * LEFT_RIGHT flips Z, FRONT_BACK flips X.
 */
class RegionTransformTest
{
    private static final BlockPos O = new BlockPos(100, 64, 200);
    private static final Vec3i SIZE = new Vec3i(3, 1, 2);

    @BeforeAll
    static void bootstrap()
    {
        MinecraftTestBootstrap.init();
    }

    private static RegionTransform simple(Mirror mirror, Rotation rotation)
    {
        return RegionTransform.of(O, mirror, rotation, BlockPos.ZERO, SIZE, Mirror.NONE, Rotation.NONE);
    }

    @Test
    void eachPlacementRotation()
    {
        assertEquals(O.offset(2, 0, 1), simple(Mirror.NONE, Rotation.NONE).toWorld(2, 0, 1));
        assertEquals(O.offset(-1, 0, 2), simple(Mirror.NONE, Rotation.CLOCKWISE_90).toWorld(2, 0, 1));
        assertEquals(O.offset(-2, 0, -1), simple(Mirror.NONE, Rotation.CLOCKWISE_180).toWorld(2, 0, 1));
        assertEquals(O.offset(1, 0, -2), simple(Mirror.NONE, Rotation.COUNTERCLOCKWISE_90).toWorld(2, 0, 1));
    }

    @Test
    void bothPlacementMirrors()
    {
        assertEquals(O.offset(2, 0, -1), simple(Mirror.LEFT_RIGHT, Rotation.NONE).toWorld(2, 0, 1));
        assertEquals(O.offset(-2, 0, 1), simple(Mirror.FRONT_BACK, Rotation.NONE).toWorld(2, 0, 1));
        // Mirror is applied before the rotation: z -> -z, then CW90 (x, z) -> (-z, x)
        assertEquals(O.offset(1, 0, 2), simple(Mirror.LEFT_RIGHT, Rotation.CLOCKWISE_90).toWorld(2, 0, 1));
    }

    @Test
    void negativeRegionSizeStartsAtTheMinimumCorner()
    {
        // Region saved from pos1 = (5,0,0) towards -X: size -3 -> covers x = 3..5 relative to the origin
        RegionTransform t = RegionTransform.of(O, Mirror.NONE, Rotation.NONE, new BlockPos(5, 0, 0), new Vec3i(-3, 1, 1),
                                               Mirror.NONE, Rotation.NONE);

        assertEquals(O.offset(3, 0, 0), t.toWorld(0, 0, 0));
        assertEquals(O.offset(5, 0, 0), t.toWorld(2, 0, 0));
    }

    @Test
    void rotatedAndOffsetSubRegion()
    {
        // Sub-region at (4,0,0), rotated CW90 on its own: local +X becomes +Z around the sub-region position
        RegionTransform sub = RegionTransform.of(O, Mirror.NONE, Rotation.NONE, new BlockPos(4, 0, 0), new Vec3i(2, 1, 1),
                                                 Mirror.NONE, Rotation.CLOCKWISE_90);
        assertEquals(O.offset(4, 0, 1), sub.toWorld(1, 0, 0));

        // Placement CW90 as well: the sub-region position turns to (0,0,4), the block offset turns twice
        RegionTransform both = RegionTransform.of(O, Mirror.NONE, Rotation.CLOCKWISE_90, new BlockPos(4, 0, 0), new Vec3i(2, 1, 1),
                                                  Mirror.NONE, Rotation.CLOCKWISE_90);
        assertEquals(O.offset(-1, 0, 4), both.toWorld(1, 0, 0));
    }

    @Test
    void roundTripForAllCombinations()
    {
        Vec3i[] sizes = { new Vec3i(5, 3, 4), new Vec3i(-5, 3, 4), new Vec3i(5, -3, -4) };
        BlockPos[] regionPositions = { BlockPos.ZERO, new BlockPos(7, -2, 3), new BlockPos(-4, 1, -9) };
        int combinations = 0;

        for (Mirror mirror : Mirror.values())
            for (Rotation rotation : Rotation.values())
                for (Mirror subMirror : Mirror.values())
                    for (Rotation subRotation : Rotation.values())
                        for (Vec3i size : sizes)
                            for (BlockPos regionPos : regionPositions)
                            {
                                RegionTransform t = RegionTransform.of(O, mirror, rotation, regionPos, size, subMirror, subRotation);
                                Set<BlockPos> worldPositions = new HashSet<>();
                                int sx = Math.abs(size.getX()), sy = Math.abs(size.getY()), sz = Math.abs(size.getZ());

                                for (int y = 0; y < sy; ++y)
                                    for (int z = 0; z < sz; ++z)
                                        for (int x = 0; x < sx; ++x)
                                        {
                                            BlockPos world = t.toWorld(x, y, z);
                                            assertEquals(new BlockPos(x, y, z), t.toLocal(world));
                                            worldPositions.add(world);
                                        }

                                assertEquals(sx * sy * sz, worldPositions.size(), "transform must be a bijection");
                                ++combinations;
                            }

        assertEquals(3 * 4 * 3 * 4 * 9, combinations);
    }

    @Test
    void worldBoxToLocalBoxMatchesBruteForce()
    {
        Random random = new Random(42);
        Vec3i size = new Vec3i(6, 4, 5);
        Vec3i containerSize = new Vec3i(6, 4, 5);

        for (Mirror mirror : Mirror.values())
            for (Rotation rotation : Rotation.values())
                for (Rotation subRotation : Rotation.values())
                {
                    RegionTransform t = RegionTransform.of(O, mirror, rotation, new BlockPos(2, 0, -3), size, Mirror.FRONT_BACK, subRotation);

                    for (int i = 0; i < 20; ++i)
                    {
                        BlockPos a = O.offset(random.nextInt(21) - 10, random.nextInt(9) - 2, random.nextInt(21) - 10);
                        BlockPos b = O.offset(random.nextInt(21) - 10, random.nextInt(9) - 2, random.nextInt(21) - 10);
                        Scope.WorldBox box = Scope.WorldBox.of(a, b);
                        ResolvedScope.LocalBox local = t.toLocalBox(box, containerSize);

                        for (int y = 0; y < 4; ++y)
                            for (int z = 0; z < 5; ++z)
                                for (int x = 0; x < 6; ++x)
                                {
                                    BlockPos w = t.toWorld(x, y, z);
                                    boolean inWorldBox = w.getX() >= box.min().getX() && w.getX() <= box.max().getX()
                                                         && w.getY() >= box.min().getY() && w.getY() <= box.max().getY()
                                                         && w.getZ() >= box.min().getZ() && w.getZ() <= box.max().getZ();
                                    boolean inLocal = local != null && local.contains(x, y, z);
                                    assertEquals(inWorldBox, inLocal, "mismatch at " + x + "," + y + "," + z + " for " + box);
                                }
                    }
                }
    }

    @Test
    void boxOutsideTheRegionGivesNull()
    {
        RegionTransform t = simple(Mirror.NONE, Rotation.CLOCKWISE_90);

        assertNull(t.toLocalBox(Scope.WorldBox.of(O.offset(50, 0, 50), O.offset(60, 5, 60)), new Vec3i(3, 1, 2)));
    }
}
