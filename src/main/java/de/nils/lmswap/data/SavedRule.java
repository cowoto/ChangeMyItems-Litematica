package de.nils.lmswap.data;

import java.util.Optional;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * A persisted replacement rule. Like the material list it works on items (decision 1): every block state of a
 * schematic that needs {@code sourceItem} is replaced with {@code targetBlock}.
 *
 * @param sourceItem  item id, e.g. "minecraft:smooth_stone_slab"
 * @param targetBlock block id, "minecraft:air" removes the material
 */
public record SavedRule(String sourceItem, String targetBlock, boolean keepProperties)
{
    public static SavedRule of(Item source, Block target, boolean keepProperties)
    {
        return new SavedRule(BuiltInRegistries.ITEM.getKey(source).toString(),
                             BuiltInRegistries.BLOCK.getKey(target).toString(), keepProperties);
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
        return obj;
    }

    @Nullable
    static SavedRule fromJson(JsonObject obj)
    {
        if (obj.has("source") && obj.has("target"))
        {
            boolean keep = obj.has("keepProperties") == false || obj.get("keepProperties").getAsBoolean();
            return new SavedRule(obj.get("source").getAsString(), obj.get("target").getAsString(), keep);
        }

        return null;
    }
}
