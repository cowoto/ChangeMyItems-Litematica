package de.nils.lmswap.core;

import java.util.List;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.client.Minecraft;

import de.nils.lmswap.core.ReplacementResult.Message;

/**
 * Connects {@link SchematicReplacer} with the running Litematica state: applies rules to a loaded schematic and
 * triggers the schematic world rebuild and the material list refresh.
 * <p>
 * Placements can share one {@link LitematicaSchematic} instance (see docs/DECISIONS.md), so a replacement always
 * affects every placement of that schematic. Callers should warn the user via {@link #getPlacementsOfSchematic}.
 * <p>
 * Must be called on the client thread with a world loaded.
 */
public final class MaterialSwapService
{
    private MaterialSwapService()
    {
    }

    public static ReplacementResult replaceInPlacement(SchematicPlacement placement, ReplacementRule rule)
    {
        return replaceInSchematic(placement.getSchematic(), List.of(rule));
    }

    public static ReplacementResult replaceInPlacement(SchematicPlacement placement, List<ReplacementRule> rules)
    {
        return replaceInSchematic(placement.getSchematic(), rules);
    }

    public static ReplacementResult replaceInSchematic(LitematicaSchematic schematic, List<ReplacementRule> rules)
    {
        if (Minecraft.getInstance().level == null)
        {
            return ReplacementResult.rejected(Message.of("lmswap.error.no_world"));
        }

        ReplacementResult result = SchematicReplacer.apply(schematic, rules);

        if (result.change() != null)
        {
            refreshAfterChange(schematic);
        }

        return result;
    }

    /**
     * Rebuilds the schematic world for all placements of the schematic. Material lists and verifiers of those
     * placements are refreshed once the rebuild has finished.
     */
    public static void refreshAfterChange(LitematicaSchematic schematic)
    {
        DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(schematic);
        RebuildWatcher.INSTANCE.refreshWhenRebuilt(schematic);
    }

    /**
     * All placements that would be affected by a replacement in the schematic of {@code placement}.
     */
    public static List<SchematicPlacement> getPlacementsOfSchematic(SchematicPlacement placement)
    {
        return getPlacementsOfSchematic(placement.getSchematic());
    }

    public static List<SchematicPlacement> getPlacementsOfSchematic(LitematicaSchematic schematic)
    {
        return DataManager.getSchematicPlacementManager().getAllPlacementsOfSchematic(schematic);
    }
}
