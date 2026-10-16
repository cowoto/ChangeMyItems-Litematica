package de.nils.lmswap.core;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

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
     * @return null if the scope can not be resolved (world boxes without placement, or not supported yet)
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

        if (scope instanceof Scope.WorldBoxes)
        {
            // World -> region conversion comes with part B (RegionTransform)
            return null;
        }

        return ResolvedScope.ALL;
    }
}
