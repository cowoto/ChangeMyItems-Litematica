package de.nils.lmswap.data;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import de.nils.lmswap.core.SchematicChange;

/**
 * In-memory undo history per loaded schematic (decision 5).
 * <p>
 * Keyed weakly by the schematic instance ({@link LitematicaSchematic} does not override equals/hashCode, so this is
 * identity based): an unloaded schematic and its history are garbage collected together. The history is cleared when
 * the schematic is re-read from its file (see LitematicaSchematicMixin), because the recorded positions would no
 * longer match. Saving does not clear it (decision 7).
 */
public final class ReplacementHistory
{
    public static final ReplacementHistory INSTANCE = new ReplacementHistory();

    /** Caps memory use; the oldest entry is dropped first */
    public static final int MAX_ENTRIES_PER_SCHEMATIC = 20;

    private final Map<LitematicaSchematic, Deque<Entry>> histories = new WeakHashMap<>();

    private ReplacementHistory()
    {
    }

    /**
     * @param sourceName display name of the replaced material
     * @param targetName display name of the new block
     * @param count      number of replaced blocks
     */
    public record Entry(SchematicChange change, String sourceName, String targetName, int count)
    {
    }

    public void push(LitematicaSchematic schematic, Entry entry)
    {
        Deque<Entry> deque = this.histories.computeIfAbsent(schematic, k -> new ArrayDeque<>());
        deque.push(entry);

        while (deque.size() > MAX_ENTRIES_PER_SCHEMATIC)
        {
            deque.removeLast();
        }
    }

    public Optional<Entry> peek(LitematicaSchematic schematic)
    {
        Deque<Entry> deque = this.histories.get(schematic);
        return deque != null ? Optional.ofNullable(deque.peek()) : Optional.empty();
    }

    public Optional<Entry> pop(LitematicaSchematic schematic)
    {
        Deque<Entry> deque = this.histories.get(schematic);
        return deque != null ? Optional.ofNullable(deque.poll()) : Optional.empty();
    }

    public int size(LitematicaSchematic schematic)
    {
        Deque<Entry> deque = this.histories.get(schematic);
        return deque != null ? deque.size() : 0;
    }

    public void clear(LitematicaSchematic schematic)
    {
        this.histories.remove(schematic);
    }
}
