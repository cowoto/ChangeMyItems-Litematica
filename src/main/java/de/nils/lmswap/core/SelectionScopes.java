package de.nils.lmswap.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;

/**
 * Reads Litematica's current area selection (SelectionManager.getCurrentSelection(), boxes in absolute world
 * coordinates) as a {@link Scope.WorldBoxes} and checks it against a placement.
 */
public final class SelectionScopes
{
    private SelectionScopes()
    {
    }

    public enum Availability
    {
        AVAILABLE,
        /** The material list has no placement (schematic list), so world coordinates can not be converted */
        NO_PLACEMENT,
        /** No area selection or it has no complete box */
        NO_SELECTION,
        /** The selection does not touch any enabled sub-region of the placement */
        NO_INTERSECTION
    }

    public static Optional<Scope.WorldBoxes> currentSelection()
    {
        AreaSelection selection = DataManager.getSelectionManager().getCurrentSelection();

        if (selection == null)
        {
            return Optional.empty();
        }

        List<Scope.WorldBox> boxes = new ArrayList<>();

        for (Box box : selection.getAllSubRegionBoxes())
        {
            if (box.getPos1() != null && box.getPos2() != null)
            {
                boxes.add(Scope.WorldBox.of(box.getPos1(), box.getPos2()));
            }
        }

        return boxes.isEmpty() ? Optional.empty() : Optional.of(new Scope.WorldBoxes(boxes));
    }

    public static Availability check(@javax.annotation.Nullable SchematicPlacement placement)
    {
        if (placement == null)
        {
            return Availability.NO_PLACEMENT;
        }

        Optional<Scope.WorldBoxes> selection = currentSelection();

        if (selection.isEmpty())
        {
            return Availability.NO_SELECTION;
        }

        ResolvedScope resolved = ScopeResolver.resolveWorldBoxes(selection.get(), placement.getSchematic(), placement);
        return ScopeResolver.isEmpty(resolved) ? Availability.NO_INTERSECTION : Availability.AVAILABLE;
    }
}
