package de.nils.lmswap.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

import de.nils.lmswap.LmSwapClient;

/**
 * MaLiLib configuration of the mod, stored in config/lmswap.json. Names, pretty names and comments come from the
 * language files (lmswap.config.generic.*), like in the masa mods.
 */
public class Configs implements IConfigHandler
{
    private static final String CONFIG_FILE_NAME = LmSwapClient.MOD_ID + ".json";
    private static final String GENERIC_KEY = LmSwapClient.MOD_ID + ".config.generic";

    public static final ConfigBoolean AUTO_APPLY_RULES = new ConfigBoolean("autoApplyRulesOnLoad", false).apply(GENERIC_KEY);
    public static final ConfigHotkey OPEN_GUI_SETTINGS = new ConfigHotkey("openGuiSettings", "M,W").apply(GENERIC_KEY);

    public static final List<IConfigBase> OPTIONS = ImmutableList.of(AUTO_APPLY_RULES, OPEN_GUI_SETTINGS);
    public static final List<IHotkey> HOTKEYS = ImmutableList.of(OPEN_GUI_SETTINGS);

    public static void loadFromFile()
    {
        Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);

        if (Files.exists(configFile) && Files.isReadable(configFile))
        {
            JsonElement element = JsonUtils.parseJsonFile(configFile);

            if (element != null && element.isJsonObject())
            {
                ConfigUtils.readConfigBase(element.getAsJsonObject(), "Generic", OPTIONS);
            }
            else
            {
                LmSwapClient.LOGGER.error("[lmswap] Failed to load config file '{}'", configFile.toAbsolutePath());
            }
        }
    }

    public static void saveToFile()
    {
        Path dir = FileUtils.getConfigDirectory();

        if (Files.exists(dir) == false)
        {
            FileUtils.createDirectoriesIfMissing(dir);
        }

        JsonObject root = new JsonObject();
        ConfigUtils.writeConfigBase(root, "Generic", OPTIONS);
        JsonUtils.writeJsonToFile(root, dir.resolve(CONFIG_FILE_NAME));
    }

    @Override
    public void load()
    {
        loadFromFile();
    }

    @Override
    public void save()
    {
        saveToFile();
    }
}
