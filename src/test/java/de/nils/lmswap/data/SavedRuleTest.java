package de.nils.lmswap.data;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedRuleTest
{
    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void jsonRoundTripAndResolve()
    {
        SavedRule rule = SavedRule.of(Items.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB, false);

        assertEquals("minecraft:smooth_stone_slab", rule.sourceItem());
        assertEquals("minecraft:stone_slab", rule.targetBlock());

        SavedRule read = SavedRule.fromJson(rule.toJson());

        assertEquals(rule, read);
        assertEquals(Items.SMOOTH_STONE_SLAB, read.resolveSource().orElseThrow());
        assertEquals(Blocks.STONE_SLAB, read.resolveTarget().orElseThrow());
    }

    @Test
    void airTargetResolvesAndUnknownIdsDoNot()
    {
        assertEquals(Blocks.AIR, new SavedRule("minecraft:dirt", "minecraft:air", true).resolveTarget().orElseThrow());
        assertTrue(new SavedRule("minecraft:dirt", "somemod:missing_block", true).resolveTarget().isEmpty());
        assertTrue(new SavedRule("not a valid id!", "minecraft:stone", true).resolveSource().isEmpty());
    }

    @Test
    void keepPropertiesDefaultsToTrueAndIncompleteJsonIsIgnored()
    {
        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        obj.addProperty("source", "minecraft:oak_slab");
        obj.addProperty("target", "minecraft:spruce_slab");

        assertTrue(SavedRule.fromJson(obj).keepProperties());

        obj.remove("target");
        assertNull(SavedRule.fromJson(obj));
    }
}
