package de.nils.lmswap.core;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What to replace and with what.
 *
 * @param sourceBlock           every state of this block is replaced
 * @param sourceState           if set, only this exact state is replaced
 * @param target                the new block; air removes the material
 * @param keepProperties        carry properties over to the target (otherwise its default state is used)
 * @param requireSameProperties reject the rule unless source and target define the same properties
 *                              (ignored for air as target), see docs/DECISIONS.md
 */
public record ReplacementRule(Block sourceBlock, @Nullable BlockState sourceState, Block target,
                              boolean keepProperties, boolean requireSameProperties)
{
    public ReplacementRule
    {
        Objects.requireNonNull(sourceBlock, "sourceBlock");
        Objects.requireNonNull(target, "target");

        if (sourceState != null && sourceState.getBlock() != sourceBlock)
        {
            throw new IllegalArgumentException("sourceState " + sourceState + " does not belong to " + sourceBlock);
        }

        if (sourceBlock.defaultBlockState().isAir())
        {
            throw new IllegalArgumentException("Air can not be replaced");
        }
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
        return new ReplacementRule(this.sourceBlock, this.sourceState, this.target, keepProperties, this.requireSameProperties);
    }

    public ReplacementRule withRequireSameProperties(boolean requireSameProperties)
    {
        return new ReplacementRule(this.sourceBlock, this.sourceState, this.target, this.keepProperties, requireSameProperties);
    }

    public boolean matches(BlockState state)
    {
        return this.sourceState != null ? state == this.sourceState : state.getBlock() == this.sourceBlock;
    }

    public boolean removesMaterial()
    {
        return this.target.defaultBlockState().isAir();
    }

    /**
     * @return false if {@link #requireSameProperties} forbids this rule
     */
    public boolean isPropertyCompatible()
    {
        return this.requireSameProperties == false
               || this.removesMaterial()
               || BlockStateMapper.haveSameProperties(this.sourceBlock, this.target);
    }

    public BlockStateMapper.MappingResult map(BlockState state)
    {
        if (this.keepProperties == false || this.removesMaterial())
        {
            return new BlockStateMapper.MappingResult(this.target.defaultBlockState(), java.util.List.of());
        }

        return BlockStateMapper.map(state, this.target);
    }
}
