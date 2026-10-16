package de.nils.lmswap.config;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;

import de.nils.lmswap.LmSwapClient;
import de.nils.lmswap.data.RuleStore;

/**
 * Registers config, config screen and hotkeys with MaLiLib, the same way the masa mods do.
 */
public class InitHandler implements IInitializationHandler, IKeybindProvider
{
    @Override
    public void registerModHandlers()
    {
        ConfigManager.getInstance().registerConfigHandler(LmSwapClient.MOD_ID, new Configs());
        Registry.CONFIG_SCREEN.registerConfigScreenFactory(new ModInfo(LmSwapClient.MOD_ID, "Litematica Material Swap", GuiConfigs::new));
        InputEventHandler.getKeybindManager().registerKeybindProvider(this);

        Configs.OPEN_GUI_SETTINGS.getKeybind().setCallback((action, key) -> {
            GuiBase.openGui(new GuiConfigs());
            return true;
        });

        RuleStore.INSTANCE.load();
    }

    @Override
    public void addKeysToMap(IKeybindManager manager)
    {
        for (IHotkey hotkey : Configs.HOTKEYS)
        {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager)
    {
        manager.addHotkeysForCategory("Litematica Material Swap", LmSwapClient.MOD_ID + ".hotkeys.category.generic", Configs.HOTKEYS);
    }
}
