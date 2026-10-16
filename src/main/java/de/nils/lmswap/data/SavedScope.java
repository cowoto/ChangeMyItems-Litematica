package de.nils.lmswap.data;

import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.nils.lmswap.core.Scope;

/**
 * Persisted scope of a rule or rule set. {@link Type#SELECTION} stores no coordinates: it means "the area selection
 * that is active when the rule is applied" and is therefore skipped by the automatic application on load.
 */
public record SavedScope(Type type, List<String> regions)
{
    public static final SavedScope ALL = new SavedScope(Type.ALL, List.of());
    public static final SavedScope SELECTION = new SavedScope(Type.SELECTION, List.of());

    public enum Type
    {
        ALL, REGIONS, SELECTION
    }

    public SavedScope
    {
        regions = List.copyOf(regions);
    }

    public static SavedScope regions(List<String> names)
    {
        return new SavedScope(Type.REGIONS, names);
    }

    /**
     * @return the scope for the replacer; empty for {@link Type#SELECTION}, which needs the current selection
     */
    public java.util.Optional<Scope> toScope()
    {
        return switch (this.type)
        {
            case ALL -> java.util.Optional.of(Scope.WHOLE);
            case REGIONS -> java.util.Optional.of(new Scope.Regions(java.util.Set.copyOf(this.regions)));
            case SELECTION -> java.util.Optional.empty();
        };
    }

    JsonObject toJson()
    {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", this.type.name().toLowerCase(Locale.ROOT));

        if (this.type == Type.REGIONS)
        {
            JsonArray arr = new JsonArray();
            this.regions.forEach(arr::add);
            obj.add("regions", arr);
        }

        return obj;
    }

    static SavedScope fromJson(JsonElement el)
    {
        if (el == null || el.isJsonObject() == false)
        {
            return ALL;
        }

        JsonObject obj = el.getAsJsonObject();
        String type = obj.has("type") ? obj.get("type").getAsString() : "all";

        return switch (type)
        {
            case "regions" -> {
                List<String> names = new java.util.ArrayList<>();

                if (obj.has("regions") && obj.get("regions").isJsonArray())
                {
                    obj.getAsJsonArray("regions").forEach(e -> names.add(e.getAsString()));
                }

                yield regions(names);
            }
            case "selection" -> SELECTION;
            default -> ALL;
        };
    }
}
