package de.nils.lmswap.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A saved batch of rules (part D): a shared scope, how chains are evaluated, and the rules in order.
 */
public record RuleSet(SavedScope scope, ChainMode chainMode, List<SavedRule> rules)
{
    public enum ChainMode
    {
        /** All rules look at the original state, one pass, first matching rule wins (default) */
        ORIGINAL,
        /** Rules are applied one after another, a rule sees the result of the previous ones */
        SEQUENTIAL
    }

    public RuleSet
    {
        rules = List.copyOf(rules);
    }

    JsonObject toJson()
    {
        JsonObject obj = new JsonObject();
        obj.add("scope", this.scope.toJson());
        obj.addProperty("chainMode", this.chainMode.name().toLowerCase(Locale.ROOT));
        JsonArray arr = new JsonArray();
        this.rules.forEach(rule -> arr.add(rule.toJson()));
        obj.add("rules", arr);
        return obj;
    }

    static RuleSet fromJson(JsonObject obj)
    {
        ChainMode mode = obj.has("chainMode") && "sequential".equals(obj.get("chainMode").getAsString())
                         ? ChainMode.SEQUENTIAL : ChainMode.ORIGINAL;
        List<SavedRule> rules = new ArrayList<>();

        if (obj.has("rules") && obj.get("rules").isJsonArray())
        {
            for (JsonElement el : obj.getAsJsonArray("rules"))
            {
                if (el.isJsonObject())
                {
                    SavedRule rule = SavedRule.fromJson(el.getAsJsonObject());

                    if (rule != null)
                    {
                        rules.add(rule);
                    }
                }
            }
        }

        return new RuleSet(SavedScope.fromJson(obj.get("scope")), mode, rules);
    }
}
