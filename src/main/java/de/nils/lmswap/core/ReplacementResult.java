package de.nils.lmswap.core;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Outcome of {@link SchematicReplacer#apply}.
 *
 * @param replacedPerRegion replaced block count per region name (only regions with changes)
 * @param warnings          non-fatal issues, as translation keys with arguments
 * @param error             set if nothing was changed because the rule was rejected
 * @param change            the undo record, null if nothing was changed
 */
public record ReplacementResult(Map<String, Integer> replacedPerRegion, List<Message> warnings,
                                @Nullable Message error, @Nullable SchematicChange change)
{
    public static ReplacementResult rejected(Message error)
    {
        return new ReplacementResult(Map.of(), List.of(), error, null);
    }

    public int totalReplaced()
    {
        return this.replacedPerRegion.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean isSuccess()
    {
        return this.error == null;
    }

    /**
     * A user facing message; the text lives in the language files.
     */
    public record Message(String translationKey, List<String> args)
    {
        public static Message of(String translationKey, Object... args)
        {
            return new Message(translationKey, java.util.Arrays.stream(args).map(String::valueOf).toList());
        }
    }
}
