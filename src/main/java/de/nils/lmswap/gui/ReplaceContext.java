package de.nils.lmswap.gui;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import de.nils.lmswap.core.MaterialSwapService;
import de.nils.lmswap.mixin.MaterialListPlacementAccessor;
import de.nils.lmswap.mixin.MaterialListSchematicAccessor;

/**
 * The schematic behind a material list, if replacing makes sense for that list.
 * <ul>
 *   <li>Placement lists: the placement's schematic, as long as the placement still exists.</li>
 *   <li>Schematic lists: only if the schematic is one of the loaded schematics. The material list opened from the
 *       file browser uses a throw-away copy that nothing else sees, so replacing there would have no effect.</li>
 *   <li>Area analyzer and custom lists have no schematic.</li>
 * </ul>
 * Litematica's "re-open last material list" hotkey can bring back a list whose placement was deleted or whose
 * schematic was unloaded, so {@link #isStillLoaded()} is checked again right before every action.
 */
public record ReplaceContext(MaterialListBase materialList, LitematicaSchematic schematic,
                             @Nullable SchematicPlacement placement)
{
    public static Optional<ReplaceContext> of(MaterialListBase materialList)
    {
        ReplaceContext context = null;

        if (materialList instanceof MaterialListPlacement)
        {
            SchematicPlacement placement = ((MaterialListPlacementAccessor) materialList).lmswap$getPlacement();
            context = new ReplaceContext(materialList, placement.getSchematic(), placement);
        }
        else if (materialList instanceof MaterialListSchematic)
        {
            LitematicaSchematic schematic = ((MaterialListSchematicAccessor) materialList).lmswap$getSchematic();
            context = new ReplaceContext(materialList, schematic, null);
        }

        return context != null && context.isStillLoaded() ? Optional.of(context) : Optional.empty();
    }

    public boolean isPlacementList()
    {
        return this.placement != null;
    }

    /**
     * False if the placement was removed or the schematic was unloaded since the list was created.
     */
    public boolean isStillLoaded()
    {
        if (SchematicHolder.getInstance().getAllSchematics().contains(this.schematic) == false)
        {
            return false;
        }

        return this.placement == null
               || DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().contains(this.placement);
    }

    public List<SchematicPlacement> placements()
    {
        return MaterialSwapService.getPlacementsOfSchematic(this.schematic);
    }

    /**
     * Placement lists are recounted by the RebuildWatcher once the schematic world is rebuilt. Schematic lists read
     * the containers directly and can be recounted right away.
     */
    public void refreshMaterialList()
    {
        if (this.isPlacementList() == false)
        {
            this.materialList.reCreateMaterialList();
        }
    }
}
