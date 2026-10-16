package de.nils.lmswap.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One rule of a batch, on item level like the material list (decision 1): every block state that counts as
 * {@code source} (and passes the conditions) is replaced with {@code target}.
 *
 * @param scope null = use the batch scope (plan decision 1: shared scope, overridable per rule)
 */
public record BatchRule(Item source, List<PropertyCondition> conditions, Block target, boolean keepProperties,
                        Map<String, String> overrides, @Nullable Scope scope)
{
    public BatchRule
    {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        conditions = List.copyOf(conditions);
        overrides = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
    }

    public static BatchRule of(Item source, Block target)
    {
        return new BatchRule(source, List.of(), target, true, Map.of(), null);
    }

    public BatchRule withConditions(List<PropertyCondition> conditions)
    {
        return new BatchRule(this.source, conditions, this.target, this.keepProperties, this.overrides, this.scope);
    }

    public BatchRule withOverrides(Map<String, String> overrides)
    {
        return new BatchRule(this.source, this.conditions, this.target, this.keepProperties, overrides, this.scope);
    }

    public BatchRule withKeepProperties(boolean keepProperties)
    {
        return new BatchRule(this.source, this.conditions, this.target, keepProperties, this.overrides, this.scope);
    }

    public BatchRule withScope(@Nullable Scope scope)
    {
        return new BatchRule(this.source, this.conditions, this.target, this.keepProperties, this.overrides, scope);
    }

    public Scope effectiveScope(Scope batchScope)
    {
        return this.scope != null ? this.scope : batchScope;
    }

    /** State part of the match (the scope is checked by position) */
    public boolean matches(BlockState state, MaterialResolver resolver)
    {
        return state.isAir() == false && resolver.sourceItem(state) == this.source
               && this.conditions.stream().allMatch(c -> c.matches(state));
    }

    public BlockState map(BlockState state)
    {
        return ReplacementRule.ofState(state, this.target).withKeepProperties(this.keepProperties)
                              .withOverrides(this.overrides).map(state).state();
    }
}
