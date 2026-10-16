package de.nils.lmswap.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import de.nils.lmswap.core.PropertyCondition;

/**
 * A persisted replacement rule. Like the material list it works on items (decision 1): every block state of a
 * schematic that needs {@code sourceItem} (and passes the {@code conditions}) is replaced with {@code targetBlock}.
 *
 * @param sourceItem  item id, e.g. "minecraft:smooth_stone_slab"
 * @param targetBlock block id, "minecraft:air" removes the material
 * @param conditions  source filter: property name -> allowed values (sorted, empty = no filter)
 * @param overrides   target properties set to a fixed value
 * @param scope       where the rule applies
 */
public record SavedRule(String sourceItem, String targetBlock, boolean keepProperties,
                        Map<String, List<String>> conditions, Map<String, String> overrides, SavedScope scope)
{
    public SavedRule
    {
        // Sorted copies, so equal rules compare equal regardless of the order in the GUI or the file
        TreeMap<String, List<String>> sortedConditions = new TreeMap<>();
        conditions.forEach((k, v) -> sortedConditions.put(k, List.copyOf(new TreeSet<>(v))));
        conditions = Collections.unmodifiableMap(sortedConditions);
        overrides = Collections.unmodifiableMap(new TreeMap<>(overrides));
    }

    /** A rule like before filters and scopes existed (and like every rule of a version 1 file) */
    public SavedRule(String sourceItem, String targetBlock, boolean keepProperties)
    {
        this(sourceItem, targetBlock, keepProperties, Map.of(), Map.of(), SavedScope.ALL);
    }

    public static SavedRule of(Item source, Block target, boolean keepProperties)
    {
        return new SavedRule(BuiltInRegistries.ITEM.getKey(source).toString(),
                             BuiltInRegistries.BLOCK.getKey(target).toString(), keepProperties);
    }

    public SavedRule withFilter(List<PropertyCondition> conditions, Map<String, String> overrides, SavedScope scope)
    {
        Map<String, List<String>> map = new LinkedHashMap<>();
        conditions.forEach(c -> map.put(c.property(), new ArrayList<>(c.values())));
        return new SavedRule(this.sourceItem, this.targetBlock, this.keepProperties, map, overrides, scope);
    }

    public List<PropertyCondition> propertyConditions()
    {
        List<PropertyCondition> list = new ArrayList<>();
        this.conditions.forEach((property, values) -> list.add(new PropertyCondition(property, java.util.Set.copyOf(values))));
        return list;
    }

    /** Two rules with the same key replace each other in the store (same source, filter and scope) */
    boolean sameKey(SavedRule other)
    {
        return this.sourceItem.equals(other.sourceItem) && this.conditions.equals(other.conditions) && this.scope.equals(other.scope);
    }

    public Optional<Item> resolveSource()
    {
        Identifier id = Identifier.tryParse(this.sourceItem);
        return id != null ? BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR) : Optional.empty();
    }

    public Optional<Block> resolveTarget()
    {
        Identifier id = Identifier.tryParse(this.targetBlock);
        return id != null && BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getOptional(id) : Optional.empty();
    }

    public String sourceName()
    {
        return this.resolveSource().map(item -> new ItemStack(item).getHoverName().getString()).orElse(this.sourceItem);
    }

    public String targetName()
    {
        return this.resolveTarget().map(SavedRule::blockName).orElse(this.targetBlock);
    }

    public static String blockName(Block block)
    {
        return block.getName().getString();
    }

    JsonObject toJson()
    {
        JsonObject obj = new JsonObject();
        obj.addProperty("source", this.sourceItem);
        obj.addProperty("target", this.targetBlock);
        obj.addProperty("keepProperties", this.keepProperties);

        JsonObject conditions = new JsonObject();
        this.conditions.forEach((property, values) -> {
            JsonArray arr = new JsonArray();
            values.forEach(arr::add);
            conditions.add(property, arr);
        });
        obj.add("conditions", conditions);

        JsonObject overrides = new JsonObject();
        this.overrides.forEach(overrides::addProperty);
        obj.add("overrides", overrides);

        obj.add("scope", this.scope.toJson());
        return obj;
    }

    /**
     * Reads version 1 and version 2 entries; fields missing in version 1 get their defaults.
     */
    @Nullable
    static SavedRule fromJson(JsonObject obj)
    {
        if (obj.has("source") == false || obj.has("target") == false)
        {
            return null;
        }

        boolean keep = obj.has("keepProperties") == false || obj.get("keepProperties").getAsBoolean();
        Map<String, List<String>> conditions = new LinkedHashMap<>();
        Map<String, String> overrides = new LinkedHashMap<>();

        if (obj.has("conditions") && obj.get("conditions").isJsonObject())
        {
            for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("conditions").entrySet())
            {
                List<String> values = new ArrayList<>();

                if (e.getValue().isJsonArray())
                {
                    e.getValue().getAsJsonArray().forEach(v -> values.add(v.getAsString()));
                }
                else
                {
                    values.add(e.getValue().getAsString());
                }

                conditions.put(e.getKey(), values);
            }
        }

        if (obj.has("overrides") && obj.get("overrides").isJsonObject())
        {
            obj.getAsJsonObject("overrides").entrySet().forEach(e -> overrides.put(e.getKey(), e.getValue().getAsString()));
        }

        return new SavedRule(obj.get("source").getAsString(), obj.get("target").getAsString(), keep,
                             conditions, overrides, SavedScope.fromJson(obj.get("scope")));
    }
}
