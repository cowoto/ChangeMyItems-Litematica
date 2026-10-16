package de.nils.lmswap.gui;

import java.util.List;
import java.util.Optional;

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
 *   <li>Placement lists: the placement's schematic.</li>
 *   <li>Schematic lists: only if the schematic is one of the loaded schematics. The material list opened from the
 *       file browser uses a throw-away copy that nothing else sees, so replacing there would have no effect.</li>
 *   <li>Area analyzer and custom lists have no schematic.</li>
 * </ul>
 */
public record ReplaceContext(MaterialListBase materialList, LitematicaSchematic schematic, boolean isPlacementList)
{
    public static Optional<ReplaceContext> of(MaterialListBase materialList)
    {
        if (materialList instanceof MaterialListPlacement)
        {
            SchematicPlacement placement = ((MaterialListPlacementAccessor) materialList).lmswap$getPlacement();
            return Optional.of(new ReplaceContext(materialList, placement.getSchematic(), true));
        }

        if (materialList instanceof MaterialListSchematic)
        {
            LitematicaSchematic schematic = ((MaterialListSchematicAccessor) materialList).lmswap$getSchematic();

            if (SchematicHolder.getInstance().getAllSchematics().contains(schematic))
            {
                return Optional.of(new ReplaceContext(materialList, schematic, false));
            }
        }

        return Optional.empty();
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
        if (this.isPlacementList == false)
        {
            this.materialList.reCreateMaterialList();
        }
    }
}
