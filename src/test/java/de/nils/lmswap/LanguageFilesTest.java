package de.nils.lmswap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every translation key used as a literal in the sources exists in de_de.json and en_us.json, and both files have the
 * same keys (CLAUDE.md: all texts via language files).
 */
class LanguageFilesTest
{
    private static final Pattern KEY = Pattern.compile("\"(lmswap\\.[a-z0-9_.]+[a-z0-9_])\"");

    private static Set<String> keys(String lang) throws IOException
    {
        Path file = Path.of("src/main/resources/assets/lmswap/lang/" + lang + ".json");
        JsonObject obj = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        return new TreeSet<>(obj.keySet());
    }

    @Test
    void allUsedKeysExistInBothLanguages() throws IOException
    {
        Set<String> used = new TreeSet<>();

        try (Stream<Path> files = Files.walk(Path.of("src/main/java")))
        {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList())
            {
                Matcher m = KEY.matcher(Files.readString(file));

                while (m.find())
                {
                    // Prefixes for MaLiLib config translations (lmswap.config.generic) are not keys themselves
                    if (m.group(1).startsWith("lmswap.config.") == false)
                    {
                        used.add(m.group(1));
                    }
                }
            }
        }

        Set<String> en = keys("en_us");
        Set<String> de = keys("de_de");
        Set<String> missingEn = new TreeSet<>(used);
        missingEn.removeAll(en);
        Set<String> missingDe = new TreeSet<>(used);
        missingDe.removeAll(de);

        assertEquals(Set.of(), missingEn, "missing in en_us.json");
        assertEquals(Set.of(), missingDe, "missing in de_de.json");
        assertEquals(en, de, "both language files must have the same keys");
    }
}
