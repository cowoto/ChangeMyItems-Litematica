package de.nils.lmswap.data;

import java.io.IOException;
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
 * Persisted replacement rules in config/lmswap_rules.json: global rules, rules per schematic file and named rule sets.
 * <p>
 * Schematic files are identified by path (see docs/DECISIONS.md): relative to Litematica's schematic base directory
 * when inside it ("base:folder/house.litematic"), otherwise absolute. Within one list a rule replaces an existing rule
 * with the same source item, filter and scope.
 * <p>
 * File format version 2 (see docs/DECISIONS.md). Version 1 files (no conditions/overrides/scope, no rule sets) are
 * migrated on load: a backup lmswap_rules.v1.bak.json is written once, then the file is saved as version 2.
 */
public final class RuleStore
{
    public static final RuleStore INSTANCE = new RuleStore();
    public static final int VERSION = 2;

    private static final String FILE_NAME = LmSwapClient.MOD_ID + "_rules.json";
    private static final String BACKUP_V1_NAME = LmSwapClient.MOD_ID + "_rules.v1.bak.json";
    private static final String BASE_PREFIX = "base:";

    private final List<SavedRule> globalRules = new ArrayList<>();
    private final Map<String, List<SavedRule>> schematicRules = new LinkedHashMap<>();
    private final Map<String, RuleSet> ruleSets = new LinkedHashMap<>();

    RuleStore()
    {
    }

    // ---- rules ----

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

    // ---- rule sets (part D) ----

    public Map<String, RuleSet> getRuleSets()
    {
        return Map.copyOf(this.ruleSets);
    }

    public void putRuleSet(String name, RuleSet set)
    {
        this.ruleSets.put(name, set);
        this.save();
    }

    public void removeRuleSet(String name)
    {
        if (this.ruleSets.remove(name) != null)
        {
            this.save();
        }
    }

    private static void put(List<SavedRule> rules, SavedRule rule)
    {
        rules.removeIf(r -> r.sameKey(rule));
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

    // ---- persistence ----

    private static Path configDir()
    {
        return FileUtils.getConfigDirectory();
    }

    public void load()
    {
        this.load(configDir().resolve(FILE_NAME), configDir().resolve(BACKUP_V1_NAME));
    }

    public void save()
    {
        this.save(configDir().resolve(FILE_NAME));
    }

    /**
     * @return true if the file was an older version and has been migrated
     */
    boolean load(Path file, Path backupFile)
    {
        this.globalRules.clear();
        this.schematicRules.clear();
        this.ruleSets.clear();

        if (Files.isReadable(file) == false)
        {
            return false;
        }

        JsonElement element = JsonUtils.parseJsonFile(file);

        if (element == null || element.isJsonObject() == false)
        {
            LmSwapClient.LOGGER.error("[lmswap] Failed to read rules file '{}'", file.toAbsolutePath());
            return false;
        }

        int version = this.fromJson(element.getAsJsonObject());

        if (version < VERSION)
        {
            try
            {
                if (Files.exists(backupFile) == false)
                {
                    Files.copy(file, backupFile);
                }
            }
            catch (IOException e)
            {
                LmSwapClient.LOGGER.error("[lmswap] Failed to back up '{}' before migration", file.toAbsolutePath(), e);
                return false; // keep the old file untouched; it is still readable
            }

            this.save(file);
            LmSwapClient.LOGGER.info("[lmswap] Migrated rules file from version {} to {} (backup: {})", version, VERSION, backupFile.getFileName());
            return true;
        }

        return false;
    }

    void save(Path file)
    {
        Path dir = file.getParent();

        if (dir != null && Files.exists(dir) == false)
        {
            FileUtils.createDirectoriesIfMissing(dir);
        }

        JsonUtils.writeJsonToFile(this.toJson(), file);
    }

    /**
     * @return the version of the read data (1 if the field is missing)
     */
    int fromJson(JsonObject root)
    {
        int version = root.has("version") ? root.get("version").getAsInt() : 1;

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

        if (root.has("ruleSets") && root.get("ruleSets").isJsonObject())
        {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("ruleSets").entrySet())
            {
                if (entry.getValue().isJsonObject())
                {
                    this.ruleSets.put(entry.getKey(), RuleSet.fromJson(entry.getValue().getAsJsonObject()));
                }
            }
        }

        return version;
    }

    JsonObject toJson()
    {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("global", writeRules(this.globalRules));

        JsonObject schematics = new JsonObject();
        this.schematicRules.forEach((key, rules) -> schematics.add(key, writeRules(rules)));
        root.add("schematics", schematics);

        JsonObject sets = new JsonObject();
        this.ruleSets.forEach((name, set) -> sets.add(name, set.toJson()));
        root.add("ruleSets", sets);

        return root;
    }

    List<SavedRule> rulesForKey(String key)
    {
        return List.copyOf(this.schematicRules.getOrDefault(key, List.of()));
    }

    /** Tests only: direct access without saving to the config directory */
    Map<String, RuleSet> ruleSetsForTest()
    {
        return this.ruleSets;
    }

    /** Tests only: direct access without saving to the config directory */
    List<SavedRule> globalRulesForTest()
    {
        return this.globalRules;
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
