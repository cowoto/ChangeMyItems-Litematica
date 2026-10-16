package de.nils.lmswap.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleStoreMigrationTest
{
    private static final String V1 = """
            {
              "version": 1,
              "global": [ { "source": "minecraft:smooth_stone_slab", "target": "minecraft:stone_slab", "keepProperties": true } ],
              "schematics": {
                "base:haus.litematic": [ { "source": "minecraft:polished_andesite", "target": "minecraft:andesite", "keepProperties": false } ]
              }
            }
            """;

    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void version1FileIsMigratedWithBackup(@TempDir Path dir) throws IOException
    {
        Path file = dir.resolve("lmswap_rules.json");
        Path backup = dir.resolve("lmswap_rules.v1.bak.json");
        Files.writeString(file, V1);

        RuleStore store = new RuleStore();

        assertTrue(store.load(file, backup), "a version 1 file is migrated");
        assertEquals(V1, Files.readString(backup), "backup is the untouched original");

        JsonObject written = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(RuleStore.VERSION, written.get("version").getAsInt());

        assertEquals(List.of(new SavedRule("minecraft:smooth_stone_slab", "minecraft:stone_slab", true)), store.getGlobalRules());
        assertEquals(List.of(new SavedRule("minecraft:polished_andesite", "minecraft:andesite", false)),
                     store.rulesForKey("base:haus.litematic"));

        // Loading the migrated file again: no second migration, same content
        RuleStore again = new RuleStore();
        assertFalse(again.load(file, backup));
        assertEquals(store.getGlobalRules(), again.getGlobalRules());
        assertEquals(store.rulesForKey("base:haus.litematic"), again.rulesForKey("base:haus.litematic"));
    }

    @Test
    void version2RoundTripKeepsFiltersOverridesScopesAndRuleSets()
    {
        SavedRule rule = new SavedRule("minecraft:smooth_stone_slab", "minecraft:stone_slab", true,
                                       Map.of("type", List.of("top", "double")), Map.of("waterlogged", "false"),
                                       SavedScope.regions(List.of("Main")));
        RuleSet set = new RuleSet(SavedScope.SELECTION, RuleSet.ChainMode.SEQUENTIAL, List.of(rule));

        RuleStore store = new RuleStore();
        store.fromJson(new JsonObject());
        store.ruleSetsForTest().put("Haus", set);
        store.globalRulesForTest().add(rule);

        RuleStore read = new RuleStore();
        assertEquals(RuleStore.VERSION, read.fromJson(store.toJson()));
        assertEquals(List.of(rule), read.getGlobalRules());
        assertEquals(set, read.getRuleSets().get("Haus"));
    }

    @Test
    void conditionOrderDoesNotMatter()
    {
        SavedRule a = new SavedRule("minecraft:oak_slab", "minecraft:spruce_slab", true,
                                    Map.of("type", List.of("top", "double")), Map.of(), SavedScope.ALL);
        SavedRule b = new SavedRule("minecraft:oak_slab", "minecraft:spruce_slab", true,
                                    Map.of("type", List.of("double", "top")), Map.of(), SavedScope.ALL);

        assertEquals(a, b);
        assertTrue(a.sameKey(b));
    }
}
