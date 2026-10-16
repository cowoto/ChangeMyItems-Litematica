package de.nils.lmswap.core;

import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Part of a source filter: the property {@code property} must have one of {@code values} (serialized value names,
 * e.g. type ∈ {top, double}). Values refer to the schematic's own orientation, not to a rotated placement
 * (see docs/DECISIONS.md).
 */
public record PropertyCondition(String property, Set<String> values)
{
    public PropertyCondition
    {
        Objects.requireNonNull(property, "property");
        values = Set.copyOf(values);
    }

    public static PropertyCondition of(String property, String... values)
    {
        return new PropertyCondition(property, Set.of(values));
    }

    public boolean matches(BlockState state)
    {
        Property<?> prop = state.getBlock().getStateDefinition().getProperty(this.property);
        return prop != null && this.values.contains(valueName(state, prop));
    }

    /**
     * @return a translation key with arguments if the condition does not fit the block, null if it is valid
     */
    @Nullable
    public ReplacementResult.Message validate(Block block)
    {
        Property<?> prop = block.getStateDefinition().getProperty(this.property);

        if (prop == null)
        {
            return ReplacementResult.Message.of("lmswap.error.filter_unknown_property", this.property, BlockStateMapper.blockId(block));
        }

        if (this.values.isEmpty())
        {
            return ReplacementResult.Message.of("lmswap.error.filter_no_values", this.property);
        }

        for (String value : this.values)
        {
            if (prop.getValue(value).isEmpty())
            {
                return ReplacementResult.Message.of("lmswap.error.filter_invalid_value", this.property, value, BlockStateMapper.blockId(block));
            }
        }

        return null;
    }

    static <T extends Comparable<T>> String valueName(BlockState state, Property<T> prop)
    {
        return prop.getName(state.getValue(prop));
    }
}
