package de.nils.lmswap.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Maps a block state onto another block, carrying over every property the target block also has.
 * Properties are matched by name, values by their serialized name, so e.g. {@code facing=up} is dropped
 * when the target only has a horizontal facing.
 */
public final class BlockStateMapper
{
    private BlockStateMapper()
    {
    }

    public static MappingResult map(BlockState source, Block target)
    {
        BlockState result = target.defaultBlockState();
        List<String> dropped = new ArrayList<>();

        for (Property<?> sourceProp : source.getProperties())
        {
            Property<?> targetProp = target.getStateDefinition().getProperty(sourceProp.getName());

            if (targetProp == null)
            {
                dropped.add(sourceProp.getName());
                continue;
            }

            Optional<BlockState> withValue = copyValue(source, sourceProp, result, targetProp);

            if (withValue.isPresent())
            {
                result = withValue.get();
            }
            else
            {
                dropped.add(sourceProp.getName());
            }
        }

        return new MappingResult(result, List.copyOf(dropped));
    }

    /**
     * Same check as Litematica's {@code BlockUtils.blocksHaveSameProperties()}:
     * both blocks define exactly the same set of properties.
     */
    public static boolean haveSameProperties(Block a, Block b)
    {
        return a.getStateDefinition().getProperties().equals(b.getStateDefinition().getProperties());
    }

    private static <S extends Comparable<S>, T extends Comparable<T>> Optional<BlockState> copyValue(
            BlockState source, Property<S> sourceProp, BlockState target, Property<T> targetProp)
    {
        String valueName = sourceProp.getName(source.getValue(sourceProp));
        return targetProp.getValue(valueName).map(value -> target.setValue(targetProp, value));
    }

    /**
     * @param state the mapped state
     * @param droppedProperties names of source properties that could not be carried over
     */
    public record MappingResult(BlockState state, List<String> droppedProperties)
    {
        public boolean isComplete()
        {
            return this.droppedProperties.isEmpty();
        }
    }
}
