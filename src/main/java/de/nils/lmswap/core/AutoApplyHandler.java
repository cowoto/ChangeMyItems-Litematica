package de.nils.lmswap.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;
import net.minecraft.client.Minecraft;

import de.nils.lmswap.config.Configs;

/**
 * Applies saved rules automatically after a schematic was read from its file (option "autoApplyRulesOnLoad").
 * <p>
 * Loading can happen before a world exists (and replacing needs the world's registries for palette resizes), so the
 * mixin only queues the schematic. The queue is processed on the client tick once a world is loaded. Only schematics
 * that ended up in Litematica's loaded schematics are processed; the throw-away copy behind the file browser's
 * material list is skipped.
 */
public final class AutoApplyHandler
{
    public static final AutoApplyHandler INSTANCE = new AutoApplyHandler();

    private final Set<LitematicaSchematic> pending = Collections.newSetFromMap(new WeakHashMap<>());

    private AutoApplyHandler()
    {
    }

    /** Called when a schematic was (re-)read from its file */
    public void onSchematicLoaded(LitematicaSchematic schematic)
    {
        this.pending.add(schematic);
    }

    public void onClientTick(Minecraft mc)
    {
        if (this.pending.isEmpty() || mc.level == null)
        {
            return;
        }

        List<LitematicaSchematic> schematics = new ArrayList<>(this.pending);
        this.pending.clear();

        if (Configs.AUTO_APPLY_RULES.getBooleanValue() == false)
        {
            return;
        }

        for (LitematicaSchematic schematic : schematics)
        {
            if (SchematicHolder.getInstance().getAllSchematics().contains(schematic) == false)
            {
                continue;
            }

            SavedRuleApplier.Summary summary = SavedRuleApplier.apply(schematic);

            if (summary.appliedRules() > 0)
            {
                InfoUtils.showGuiOrInGameMessage(MessageType.INFO, "lmswap.info.rules_auto_applied", summary.appliedRules(),
                                                 summary.blocks(), schematic.getMetadata().getName());
            }

            if (summary.skippedSelection() > 0)
            {
                InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "lmswap.warning.rules_selection_skipped",
                                                 summary.skippedSelection(), schematic.getMetadata().getName());
            }

            if (summary.skipped() > 0)
            {
                InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "lmswap.warning.rules_skipped", summary.skipped(),
                                                 schematic.getMetadata().getName());
            }
        }
    }
}
