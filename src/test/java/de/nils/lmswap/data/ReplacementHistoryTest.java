package de.nils.lmswap.data;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;

import de.nils.lmswap.core.SchematicChange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ReplacementHistoryTest
{
    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ReplacementHistory.Entry entry(int positions)
    {
        IntArrayList indices = new IntArrayList(new int[positions]);
        SchematicChange change = new SchematicChange(List.of(new SchematicChange.RegionChange(
                "A", Map.of(Blocks.STONE.defaultBlockState(), indices), Map.of(), Map.of())), 0);
        return new ReplacementHistory.Entry(change, "a", "b", positions);
    }

    @Test
    void entryCountIsCapped()
    {
        Deque<ReplacementHistory.Entry> deque = new ArrayDeque<>();

        for (int i = 0; i < ReplacementHistory.MAX_ENTRIES_PER_SCHEMATIC + 5; ++i)
        {
            deque.push(entry(1));
            ReplacementHistory.trim(deque);
        }

        assertEquals(ReplacementHistory.MAX_ENTRIES_PER_SCHEMATIC, deque.size());
    }

    @Test
    void positionBudgetDropsOldestButKeepsNewest()
    {
        Deque<ReplacementHistory.Entry> deque = new ArrayDeque<>();
        int third = (int) (ReplacementHistory.MAX_POSITIONS_PER_SCHEMATIC / 3) + 1;

        deque.push(entry(third));
        deque.push(entry(third));
        ReplacementHistory.trim(deque);
        assertEquals(2, deque.size());

        deque.push(entry(third));
        ReplacementHistory.trim(deque);
        assertEquals(2, deque.size(), "three thirds exceed the budget, the oldest is dropped");

        ReplacementHistory.Entry huge = entry((int) ReplacementHistory.MAX_POSITIONS_PER_SCHEMATIC + 10);
        deque.push(huge);
        ReplacementHistory.trim(deque);
        assertEquals(1, deque.size());
        assertSame(huge, deque.peek(), "the newest entry is always kept, even if it alone exceeds the budget");
    }
}
