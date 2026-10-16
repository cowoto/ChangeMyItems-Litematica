package de.nils.lmswap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.dy.masa.malilib.event.InitializationHandler;

import de.nils.lmswap.config.InitHandler;
import de.nils.lmswap.core.AutoApplyHandler;
import de.nils.lmswap.core.RebuildWatcher;

public class LmSwapClient implements ClientModInitializer
{
    public static final String MOD_ID = "lmswap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static String getVersion()
    {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public void onInitializeClient()
    {
        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            AutoApplyHandler.INSTANCE.onClientTick(mc);
            RebuildWatcher.INSTANCE.onClientTick(mc);
        });

        LOGGER.info("[lmswap] Litematica Material Swap {} initialized", getVersion());
    }
}
