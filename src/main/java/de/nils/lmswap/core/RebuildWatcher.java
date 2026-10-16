package de.nils.lmswap.core;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.PlacementManagerDaemonHandler;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import net.minecraft.client.Minecraft;

import de.nils.lmswap.mixin.SchematicPlacementAccessor;

/**
 * Waits until Litematica's placement daemon has finished rebuilding the schematic world, then recounts the
 * material lists and restarts the verifiers of the affected placements (both read from the schematic world).
 * <p>
 * Litematica has no "rebuild finished" signal: tasks are removed from the queue when a thread picks them up, so an
 * empty queue can still mean one task is running. We therefore wait until the queues stayed empty for a few ticks.
 */
public final class RebuildWatcher
{
    public static final RebuildWatcher INSTANCE = new RebuildWatcher();

    /** Ticks the daemon queues must stay empty before we consider the rebuild done */
    private static final int QUIET_TICKS = 10;

    private final Set<LitematicaSchematic> pending = Collections.newSetFromMap(new IdentityHashMap<>());
    private int quietTicks;

    private RebuildWatcher()
    {
    }

    public void refreshWhenRebuilt(LitematicaSchematic schematic)
    {
        this.pending.add(schematic);
        this.quietTicks = 0;
    }

    public void onClientTick(Minecraft mc)
    {
        if (this.pending.isEmpty())
        {
            return;
        }

        if (mc.level == null)
        {
            this.pending.clear();
            return;
        }

        if (PlacementManagerDaemonHandler.INSTANCE.hasAnyTasks())
        {
            this.quietTicks = 0;
            return;
        }

        if (++this.quietTicks < QUIET_TICKS)
        {
            return;
        }

        for (LitematicaSchematic schematic : this.pending)
        {
            for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllPlacementsOfSchematic(schematic))
            {
                refreshPlacement(mc, placement);
            }
        }

        this.pending.clear();
    }

    private static void refreshPlacement(Minecraft mc, SchematicPlacement placement)
    {
        MaterialListBase materialList = ((SchematicPlacementAccessor) placement).lmswap$getMaterialListOrNull();

        // Lists that were never opened are counted on first access anyway
        if (materialList != null)
        {
            materialList.reCreateMaterialList();
        }

        if (placement.hasVerifier())
        {
            SchematicVerifier verifier = placement.getSchematicVerifier();

            if (verifier.isActive() || verifier.isPaused() || verifier.isFinished())
            {
                verifier.startVerification(mc.level, SchematicWorldHandler.getSchematicWorld(), placement, null);
            }
        }
    }
}
