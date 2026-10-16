package de.nils.lmswap.config;

import java.util.List;

import fi.dy.masa.malilib.gui.GuiConfigsBase;

import de.nils.lmswap.LmSwapClient;

/**
 * Config page in MaLiLib style. Reachable via hotkey (default M,W), MaLiLib's config screen switcher and Mod Menu.
 */
public class GuiConfigs extends GuiConfigsBase
{
    public GuiConfigs()
    {
        super(10, 26, LmSwapClient.MOD_ID, null, "lmswap.gui.title.configs", LmSwapClient.getVersion());
    }

    @Override
    protected int getConfigWidth()
    {
        return 200;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs()
    {
        return ConfigOptionWrapper.createFor(Configs.OPTIONS);
    }
}
