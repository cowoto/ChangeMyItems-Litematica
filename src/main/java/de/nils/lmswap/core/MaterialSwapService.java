package de.nils.lmswap.core;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.client.Minecraft;

import de.nils.lmswap.core.ReplacementResult.Message;
import de.nils.lmswap.data.ReplacementHistory;

/**
 * Connects {@link SchematicReplacer} with the running Litematica state: applies rules to a loaded schematic, records
 * the undo history and triggers the schematic world rebuild and the material list refresh.
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
        return replaceInSchematic(placement.getSchematic(), List.of(rule), "?", "?");
    }

    /**
     * @param sourceName display name of the replaced material, for the undo history
     * @param targetName display name of the new block, for the undo history
     */
    public static ReplacementResult replaceInSchematic(LitematicaSchematic schematic, List<ReplacementRule> rules,
                                                       String sourceName, String targetName)
    {
        return replaceInSchematic(schematic, rules, null, sourceName, targetName);
    }

    /**
     * @param placement needed for rules with a world box scope (area selection), may be null otherwise
     */
    public static ReplacementResult replaceInSchematic(LitematicaSchematic schematic, List<ReplacementRule> rules,
                                                       @Nullable SchematicPlacement placement,
                                                       String sourceName, String targetName)
    {
        if (Minecraft.getInstance().level == null)
        {
            return ReplacementResult.rejected(Message.of("lmswap.error.no_world"));
        }

        ReplacementResult result = SchematicReplacer.apply(schematic, rules, placement);

        if (result.change() != null)
        {
            ReplacementHistory.INSTANCE.push(schematic, new ReplacementHistory.Entry(result.change(), sourceName,
                                                                                     targetName, result.totalReplaced()));
            refreshAfterChange(schematic);
        }

        return result;
    }

    /**
     * Reverts the most recent replacement of the schematic.
     *
     * @return the reverted entry, empty if there was nothing to undo or no world is loaded
     */
    public static Optional<ReplacementHistory.Entry> undoLast(LitematicaSchematic schematic)
    {
        if (Minecraft.getInstance().level == null)
        {
            return Optional.empty();
        }

        Optional<ReplacementHistory.Entry> entry = ReplacementHistory.INSTANCE.pop(schematic);

        entry.ifPresent(e -> {
            SchematicReplacer.revert(schematic, e.change());
            refreshAfterChange(schematic);
        });

        return entry;
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

    public static List<SchematicPlacement> getPlacementsOfSchematic(LitematicaSchematic schematic)
    {
        return DataManager.getSchematicPlacementManager().getAllPlacementsOfSchematic(schematic);
    }
}
