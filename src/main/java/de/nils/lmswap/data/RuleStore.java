package de.nils.lmswap.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

import de.nils.lmswap.LmSwapClient;

/**
 * Persisted replacement rules in config/lmswap_rules.json: global rules plus rules per schematic file.
 * <p>
 * Schematic files are identified by path (see docs/DECISIONS.md): relative to Litematica's schematic base directory
 * when inside it ("base:folder/house.litematic"), otherwise absolute. At most one rule per source item and scope;
 * adding a rule for the same source replaces the old one.
 */
public final class RuleStore
{
    public static final RuleStore INSTANCE = new RuleStore();

    private static final String FILE_NAME = LmSwapClient.MOD_ID + "_rules.json";
    private static final String BASE_PREFIX = "base:";

    private final List<SavedRule> globalRules = new ArrayList<>();
    private final Map<String, List<SavedRule>> schematicRules = new LinkedHashMap<>();

    private RuleStore()
    {
    }

    public List<SavedRule> getGlobalRules()
    {
        return List.copyOf(this.globalRules);
    }

    /** Rules for the file of this schematic; empty for schematics without a file */
    public List<SavedRule> getSchematicRules(LitematicaSchematic schematic)
    {
        String key = keyFor(schematic);
        return key != null ? List.copyOf(this.schematicRules.getOrDefault(key, List.of())) : List.of();
    }

    public boolean canStoreFor(LitematicaSchematic schematic)
    {
        return keyFor(schematic) != null;
    }

    public void putGlobal(SavedRule rule)
    {
        put(this.globalRules, rule);
        this.save();
    }

    public void putForSchematic(LitematicaSchematic schematic, SavedRule rule)
    {
        String key = keyFor(schematic);

        if (key != null)
        {
            put(this.schematicRules.computeIfAbsent(key, k -> new ArrayList<>()), rule);
            this.save();
        }
    }

    public void removeGlobal(SavedRule rule)
    {
        if (this.globalRules.remove(rule))
        {
            this.save();
        }
    }

    public void removeForSchematic(LitematicaSchematic schematic, SavedRule rule)
    {
        String key = keyFor(schematic);
        List<SavedRule> rules = key != null ? this.schematicRules.get(key) : null;

        if (rules != null && rules.remove(rule))
        {
            if (rules.isEmpty())
            {
                this.schematicRules.remove(key);
            }

            this.save();
        }
    }

    private static void put(List<SavedRule> rules, SavedRule rule)
    {
        rules.removeIf(r -> r.sourceItem().equals(rule.sourceItem()));
        rules.add(rule);
    }

    @Nullable
    static String keyFor(LitematicaSchematic schematic)
    {
        Path file = schematic.getFile();

        if (file == null)
        {
            return null;
        }

        Path normalized = file.toAbsolutePath().normalize();
        Path base = DataManager.getSchematicsBaseDirectory().toAbsolutePath().normalize();

        if (normalized.startsWith(base))
        {
            return BASE_PREFIX + base.relativize(normalized).toString().replace('\\', '/');
        }

        return normalized.toString().replace('\\', '/');
    }

    private Path getFile()
    {
        return FileUtils.getConfigDirectory().resolve(FILE_NAME);
    }

    public void load()
    {
        this.globalRules.clear();
        this.schematicRules.clear();

        Path file = this.getFile();

        if (Files.isReadable(file) == false)
        {
            return;
        }

        JsonElement element = JsonUtils.parseJsonFile(file);

        if (element == null || element.isJsonObject() == false)
        {
            LmSwapClient.LOGGER.error("[lmswap] Failed to read rules file '{}'", file.toAbsolutePath());
            return;
        }

        JsonObject root = element.getAsJsonObject();

        if (root.has("global") && root.get("global").isJsonArray())
        {
            readRules(root.getAsJsonArray("global"), this.globalRules);
        }

        if (root.has("schematics") && root.get("schematics").isJsonObject())
        {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("schematics").entrySet())
            {
                if (entry.getValue().isJsonArray())
                {
                    List<SavedRule> rules = new ArrayList<>();
                    readRules(entry.getValue().getAsJsonArray(), rules);

                    if (rules.isEmpty() == false)
                    {
                        this.schematicRules.put(entry.getKey(), rules);
                    }
                }
            }
        }
    }

    public void save()
    {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("global", writeRules(this.globalRules));

        JsonObject schematics = new JsonObject();
        this.schematicRules.forEach((key, rules) -> schematics.add(key, writeRules(rules)));
        root.add("schematics", schematics);

        Path dir = FileUtils.getConfigDirectory();

        if (Files.exists(dir) == false)
        {
            FileUtils.createDirectoriesIfMissing(dir);
        }

        JsonUtils.writeJsonToFile(root, this.getFile());
    }

    private static void readRules(JsonArray array, List<SavedRule> out)
    {
        for (JsonElement el : array)
        {
            if (el.isJsonObject())
            {
                SavedRule rule = SavedRule.fromJson(el.getAsJsonObject());

                if (rule != null)
                {
                    put(out, rule);
                }
            }
        }
    }

    private static JsonArray writeRules(List<SavedRule> rules)
    {
        JsonArray array = new JsonArray();
        rules.forEach(rule -> array.add(rule.toJson()));
        return array;
    }
}
