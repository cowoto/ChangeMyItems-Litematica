package de.nils.lmswap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.nils.lmswap.core.RebuildWatcher;

public class LmSwapClient implements ClientModInitializer
{
    public static final String MOD_ID = "lmswap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient()
    {
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");

        ClientTickEvents.END_CLIENT_TICK.register(RebuildWatcher.INSTANCE::onClientTick);

        LOGGER.info("[lmswap] Litematica Material Swap {} initialized", version);
    }
}
