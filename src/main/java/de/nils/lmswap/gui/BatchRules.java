package de.nils.lmswap.gui;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import de.nils.lmswap.core.BatchRule;
import de.nils.lmswap.core.ReplacementResult.Message;
import de.nils.lmswap.core.Scope;
import de.nils.lmswap.core.SelectionScopes;
import de.nils.lmswap.data.SavedRule;
import de.nils.lmswap.data.SavedScope;

/**
 * Converts the persistable rule form ({@link SavedRule}) used by the batch screen and rule sets into executable
 * {@link BatchRule}s. Scope "selection" is resolved to the current area selection at that moment.
 */
final class BatchRules
{
    private BatchRules()
    {
    }

    /**
     * @param rule  converted rule, null if not convertible
     * @param error why not, null if fine
     */
    record Converted(@Nullable BatchRule rule, @Nullable Message error)
    {
    }

    static Converted convert(SavedRule saved)
    {
        Optional<Item> source = saved.resolveSource();
        Optional<Block> target = saved.resolveTarget();

        if (source.isEmpty() || target.isEmpty())
        {
            return new Converted(null, Message.of("lmswap.error.rule_unknown_id", source.isEmpty() ? saved.sourceItem() : saved.targetBlock()));
        }

        Scope scope = null;

        if (saved.scope().type() == SavedScope.Type.SELECTION)
        {
            Optional<Scope.WorldBoxes> selection = SelectionScopes.currentSelection();

            if (selection.isEmpty())
            {
                return new Converted(null, Message.of("lmswap.gui.scope.selection_unavailable.no_selection"));
            }

            scope = selection.get();
        }
        else if (saved.scope().type() != SavedScope.Type.INHERIT)
        {
            scope = saved.scope().toScope().orElse(Scope.WHOLE);
        }

        return new Converted(new BatchRule(source.get(), saved.propertyConditions(), target.get(), saved.keepProperties(),
                                           saved.overrides(), scope), null);
    }

    /**
     * The batch scope itself; "inherit" makes no sense there and means the whole schematic.
     *
     * @return null if the scope is the area selection and there is none
     */
    @Nullable
    static Scope batchScope(SavedScope saved)
    {
        if (saved.type() == SavedScope.Type.SELECTION)
        {
            return SelectionScopes.currentSelection().orElse(null);
        }

        return saved.toScope().orElse(Scope.WHOLE);
    }
}
