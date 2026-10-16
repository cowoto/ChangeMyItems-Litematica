package de.nils.lmswap.debug;

import java.util.List;
import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

import de.nils.lmswap.core.MaterialSwapService;
import de.nils.lmswap.core.ReplacementResult;
import de.nils.lmswap.core.ReplacementRule;

/**
 * Development only: {@code /lmswap-debug swap [confirm]} replaces smooth_stone_slab with stone_slab
 * in the selected placement. Registered only in the development environment.
 */
public final class DebugCommands
{
    private DebugCommands()
    {
    }

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        dispatcher.register(ClientCommands.literal("lmswap-debug")
                .then(ClientCommands.literal("swap")
                        .executes(ctx -> swap(ctx, false))
                        .then(ClientCommands.literal("confirm")
                                .executes(ctx -> swap(ctx, true)))));
    }

    private static int swap(CommandContext<FabricClientCommandSource> ctx, boolean confirmed)
    {
        FabricClientCommandSource source = ctx.getSource();
        SchematicPlacement placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();

        if (placement == null)
        {
            source.sendError(Component.translatable("lmswap.error.no_placement_selected"));
            return 0;
        }

        List<SchematicPlacement> affected = MaterialSwapService.getPlacementsOfSchematic(placement);

        if (affected.size() > 1 && confirmed == false)
        {
            source.sendFeedback(Component.translatable("lmswap.warning.shared_schematic", affected.size()));
            source.sendFeedback(Component.translatable("lmswap.debug.confirm_hint", "/lmswap-debug swap confirm"));
            return 0;
        }

        ReplacementRule rule = ReplacementRule.ofBlock(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB);
        ReplacementResult result = MaterialSwapService.replaceInPlacement(placement, rule);

        if (result.isSuccess() == false)
        {
            source.sendError(translate(result.error()));
            return 0;
        }

        source.sendFeedback(Component.translatable("lmswap.info.replaced_total", result.totalReplaced(), placement.getName()));

        for (Map.Entry<String, Integer> entry : result.replacedPerRegion().entrySet())
        {
            source.sendFeedback(Component.translatable("lmswap.info.replaced_region", entry.getKey(), entry.getValue()));
        }

        for (ReplacementResult.Message warning : result.warnings())
        {
            source.sendFeedback(translate(warning));
        }

        return result.totalReplaced();
    }

    private static Component translate(ReplacementResult.Message message)
    {
        return Component.translatable(message.translationKey(), message.args().toArray());
    }
}
