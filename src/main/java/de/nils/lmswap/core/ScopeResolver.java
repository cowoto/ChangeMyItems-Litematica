package de.nils.lmswap.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import de.nils.lmswap.core.ResolvedScope.LocalBox;
import de.nils.lmswap.core.ResolvedScope.RegionPart;

/**
 * Converts a {@link Scope} into region coordinates of a schematic.
 */
public final class ScopeResolver
{
    private ScopeResolver()
    {
    }

    /**
     * @param placement needed for {@link Scope.WorldBoxes}; may be null otherwise
     * @return null if the scope can not be resolved (world boxes without placement)
     */
    @Nullable
    public static ResolvedScope resolve(Scope scope, LitematicaSchematic schematic, @Nullable SchematicPlacement placement)
    {
        if (scope instanceof Scope.Regions regions)
        {
            Map<String, RegionPart> map = new HashMap<>();

            for (String name : regions.names())
            {
                if (schematic.getSubRegionContainer(name) != null)
                {
                    map.put(name, RegionPart.WHOLE);
                }
            }

            return ResolvedScope.of(map);
        }

        if (scope instanceof Scope.WorldBoxes boxes)
        {
            return placement != null && placement.getSchematic() == schematic ? resolveWorldBoxes(boxes, schematic, placement) : null;
        }

        return ResolvedScope.ALL;
    }

    /**
     * World boxes -> per enabled sub-region of the placement the intersecting container boxes. Disabled sub-regions
     * are not shown in the world, so a selection can not meaningfully target them (same as Litematica's own edits).
     */
    static ResolvedScope resolveWorldBoxes(Scope.WorldBoxes boxes, LitematicaSchematic schematic, SchematicPlacement placement)
    {
        Map<String, RegionPart> map = new HashMap<>();

        for (String regionName : placement.getEnabledRelativeSubRegionPlacements().keySet())
        {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);
            RegionTransform transform = RegionTransform.of(placement, regionName);

            if (container == null || transform == null)
            {
                continue;
            }

            List<LocalBox> local = localBoxes(transform, boxes, container);

            if (local.isEmpty() == false)
            {
                map.put(regionName, new RegionPart(local));
            }
        }

        return ResolvedScope.of(map);
    }

    static List<LocalBox> localBoxes(RegionTransform transform, Scope.WorldBoxes boxes, LitematicaBlockStateContainer container)
    {
        List<LocalBox> local = new ArrayList<>();

        for (Scope.WorldBox box : boxes.boxes())
        {
            LocalBox lb = transform.toLocalBox(box, container.getSize());

            if (lb != null)
            {
                local.add(lb);
            }
        }

        return local;
    }

    /** True if the resolved scope covers no block position at all (e.g. a selection that misses the placement) */
    public static boolean isEmpty(ResolvedScope scope)
    {
        return scope.isAll() == false && scope.isEmptyMap();
    }
}
