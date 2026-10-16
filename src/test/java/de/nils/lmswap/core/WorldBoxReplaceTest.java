package de.nils.lmswap.core;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.selection.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Part B: replacing only inside a world box of a rotated and mirrored placement replaces exactly the blocks whose
 * world position lies in the box.
 */
class WorldBoxReplaceTest
{
    @BeforeAll
    static void bootstrap()
    {
        MinecraftTestBootstrap.init();
    }

    @Test
    void onlyBlocksInsideTheWorldBoxAreReplaced()
    {
        BlockPos origin = new BlockPos(-30, 70, 15);

        for (Rotation rotation : Rotation.values())
        {
            for (Mirror mirror : Mirror.values())
            {
                LitematicaSchematic schematic = SchematicReplacerSchematicTest.schematic(new Box(BlockPos.ZERO, new BlockPos(5, 2, 4), "A"));
                LitematicaBlockStateContainer c = schematic.getSubRegionContainer("A");
                Vec3i size = c.getSize();

                for (int y = 0; y < size.getY(); ++y)
                    for (int z = 0; z < size.getZ(); ++z)
                        for (int x = 0; x < size.getX(); ++x)
                            c.set(x, y, z, Blocks.SMOOTH_STONE.defaultBlockState());

                RegionTransform transform = RegionTransform.of(origin, mirror, rotation, BlockPos.ZERO, size, Mirror.NONE, Rotation.NONE);

                // A corner: the world box covers the 2x2x2 world corner at the transformed local origin
                BlockPos corner = transform.toWorld(0, 0, 0);
                Scope.WorldBox box = Scope.WorldBox.of(corner, corner.offset(rotation == Rotation.NONE ? 1 : -1, 1, 1));
                Scope.WorldBoxes boxes = new Scope.WorldBoxes(List.of(box));

                ResolvedScope resolved = ResolvedScope.of(Map.of("A", new ResolvedScope.RegionPart(ScopeResolver.localBoxes(transform, boxes, c))));
                ReplacementRule rule = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE, Blocks.STONE).withScope(boxes);

                ReplacementResult result = SchematicReplacer.applyResolved(schematic, List.of(rule), new ResolvedScope[] { resolved });

                int expected = 0;

                for (int y = 0; y < size.getY(); ++y)
                    for (int z = 0; z < size.getZ(); ++z)
                        for (int x = 0; x < size.getX(); ++x)
                        {
                            BlockPos w = transform.toWorld(x, y, z);
                            boolean inBox = w.getX() >= box.min().getX() && w.getX() <= box.max().getX()
                                            && w.getY() >= box.min().getY() && w.getY() <= box.max().getY()
                                            && w.getZ() >= box.min().getZ() && w.getZ() <= box.max().getZ();
                            expected += inBox ? 1 : 0;
                            assertEquals(inBox ? Blocks.STONE : Blocks.SMOOTH_STONE, c.get(x, y, z).getBlock(),
                                         rotation + "/" + mirror + " at " + x + "," + y + "," + z);
                        }

                assertEquals(expected, result.totalReplaced(), rotation + "/" + mirror);
                assertEquals(true, expected > 0 && expected < size.getX() * size.getY() * size.getZ(), "box must be a real part");
            }
        }
    }
}
