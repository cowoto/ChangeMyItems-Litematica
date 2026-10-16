package de.nils.lmswap.core;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;

/**
 * Registers blocks, items and properties. Requires the tests to run through fabric-loader-junit.
 */
final class MinecraftTestBootstrap
{
    private MinecraftTestBootstrap()
    {
    }

    static void init()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        // In game Litematica sets this on world join. Palette resizes re-read block states through it.
        SchematicWorldHandler.INSTANCE.setDynamicRegistryManager(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
