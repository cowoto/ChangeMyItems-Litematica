package de.nils.lmswap.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import de.nils.lmswap.core.ReplacementResult.Message;

/**
 * What to replace, with what and where.
 * <p>
 * A rule without conditions, without overrides and with {@link Scope#WHOLE} behaves exactly like the rules before
 * filters and scopes existed ({@link #ofBlock}, {@link #ofState}).
 *
 * @param sourceBlock           every state of this block is a candidate
 * @param sourceState           if set, only this exact state is a candidate
 * @param target                the new block; air removes the material
 * @param keepProperties        carry properties over to the target (otherwise its default state is used)
 * @param requireSameProperties reject the rule unless source and target define the same properties
 *                              (ignored for air as target), see docs/DECISIONS.md
 * @param conditions            source filter: all conditions must match (empty = every state)
 * @param overrides             target properties that are always set to the given value, after keepProperties
 * @param scope                 where the rule applies
 */
public record ReplacementRule(Block sourceBlock, @Nullable BlockState sourceState, Block target,
                              boolean keepProperties, boolean requireSameProperties,
                              List<PropertyCondition> conditions, Map<String, String> overrides, Scope scope)
{
    public ReplacementRule
    {
        Objects.requireNonNull(sourceBlock, "sourceBlock");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(scope, "scope");
        conditions = List.copyOf(conditions);
        overrides = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(overrides));

        if (sourceState != null && sourceState.getBlock() != sourceBlock)
        {
            throw new IllegalArgumentException("sourceState " + sourceState + " does not belong to " + sourceBlock);
        }

        if (sourceBlock.defaultBlockState().isAir())
        {
            throw new IllegalArgumentException("Air can not be replaced");
        }
    }

    public ReplacementRule(Block sourceBlock, @Nullable BlockState sourceState, Block target,
                           boolean keepProperties, boolean requireSameProperties)
    {
        this(sourceBlock, sourceState, target, keepProperties, requireSameProperties, List.of(), Map.of(), Scope.WHOLE);
    }

    public static ReplacementRule ofBlock(Block source, Block target)
    {
        return new ReplacementRule(source, null, target, true, true);
    }

    public static ReplacementRule ofState(BlockState source, Block target)
    {
        return new ReplacementRule(source.getBlock(), source, target, true, true);
    }

    public ReplacementRule withKeepProperties(boolean keepProperties)
    {
        return new ReplacementRule(this.sourceBlock, this.sourceState, this.target, keepProperties, this.requireSameProperties,
                                   this.conditions, this.overrides, this.scope);
    }

    public ReplacementRule withRequireSameProperties(boolean requireSameProperties)
    {
        return new ReplacementRule(this.sourceBlock, this.sourceState, this.target, this.keepProperties, requireSameProperties,
                                   this.conditions, this.overrides, this.scope);
    }

    public ReplacementRule withConditions(List<PropertyCondition> conditions)
    {
        return new ReplacementRule(this.sourceBlock, this.sourceState, this.target, this.keepProperties, this.requireSameProperties,
                                   conditions, this.overrides, this.scope);
    }

    public ReplacementRule withOverrides(Map<String, String> overrides)
    {
        return new ReplacementRule(this.sourceBlock, this.sourceState, this.target, this.keepProperties, this.requireSameProperties,
                                   this.conditions, overrides, this.scope);
    }

    public ReplacementRule withScope(Scope scope)
    {
        return new ReplacementRule(this.sourceBlock, this.sourceState, this.target, this.keepProperties, this.requireSameProperties,
                                   this.conditions, this.overrides, scope);
    }

    /**
     * State part of the match; the scope (position) is checked separately by the replacer.
     */
    public boolean matches(BlockState state)
    {
        if (this.sourceState != null)
        {
            return state == this.sourceState;
        }

        if (state.getBlock() != this.sourceBlock)
        {
            return false;
        }

        for (PropertyCondition condition : this.conditions)
        {
            if (condition.matches(state) == false)
            {
                return false;
            }
        }

        return true;
    }

    public boolean removesMaterial()
    {
        return this.target.defaultBlockState().isAir();
    }

    /**
     * @return false if {@link #requireSameProperties} forbids this rule. Without {@link #keepProperties} the target
     *         gets its default state, so the property sets do not matter.
     */
    public boolean isPropertyCompatible()
    {
        return this.requireSameProperties == false
               || this.keepProperties == false
               || this.removesMaterial()
               || BlockStateMapper.haveSameProperties(this.sourceBlock, this.target);
    }

    /**
     * Checks conditions against the source block and overrides against the target block.
     *
     * @return the problems, empty if the rule is valid
     */
    public List<Message> validate()
    {
        List<Message> errors = new ArrayList<>();

        for (PropertyCondition condition : this.conditions)
        {
            Message error = condition.validate(this.sourceBlock);

            if (error != null)
            {
                errors.add(error);
            }
        }

        errors.addAll(validateOverrides(this.target, this.overrides));
        return errors;
    }

    /**
     * Overrides must name properties and values the target block has.
     */
    public static List<Message> validateOverrides(Block target, Map<String, String> overrides)
    {
        List<Message> errors = new ArrayList<>();

        for (Map.Entry<String, String> override : overrides.entrySet())
        {
            Property<?> prop = target.getStateDefinition().getProperty(override.getKey());

            if (prop == null)
            {
                errors.add(Message.of("lmswap.error.override_unknown_property", override.getKey(), BlockStateMapper.blockId(target)));
            }
            else if (prop.getValue(override.getValue()).isEmpty())
            {
                errors.add(Message.of("lmswap.error.override_invalid_value", override.getKey(), override.getValue(),
                                      BlockStateMapper.blockId(target)));
            }
        }

        return errors;
    }

    public BlockStateMapper.MappingResult map(BlockState state)
    {
        BlockStateMapper.MappingResult result;

        if (this.keepProperties == false || this.removesMaterial())
        {
            result = new BlockStateMapper.MappingResult(this.target.defaultBlockState(), List.of());
        }
        else
        {
            result = BlockStateMapper.map(state, this.target);
        }

        if (this.overrides.isEmpty())
        {
            return result;
        }

        BlockState overridden = result.state();

        for (Map.Entry<String, String> override : this.overrides.entrySet())
        {
            overridden = applyOverride(overridden, override.getKey(), override.getValue());
        }

        return new BlockStateMapper.MappingResult(overridden, result.droppedProperties());
    }

    private static BlockState applyOverride(BlockState state, String property, String value)
    {
        Property<?> prop = state.getBlock().getStateDefinition().getProperty(property);
        return prop != null ? setValue(state, prop, value) : state;
    }

    private static <T extends Comparable<T>> BlockState setValue(BlockState state, Property<T> prop, String value)
    {
        return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
    }
}
