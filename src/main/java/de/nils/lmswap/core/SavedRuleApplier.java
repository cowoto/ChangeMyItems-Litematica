package de.nils.lmswap.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import de.nils.lmswap.data.RuleStore;
import de.nils.lmswap.data.SavedRule;

/**
 * Applies the saved rules to a loaded schematic: first the rules of its file, then the global rules for source items
 * that have no schematic specific rule. Every applied rule becomes a normal undo history entry.
 */
public final class SavedRuleApplier
{
    private SavedRuleApplier()
    {
    }

    /**
     * @param appliedRules number of rules that changed something
     * @param blocks       total replaced blocks
     * @param skipped      rules that could not be applied (unknown ids, strict property check)
     */
    public record Summary(int appliedRules, int blocks, int skipped)
    {
    }

    /** The rules that apply to this schematic, in application order */
    public static List<SavedRule> effectiveRules(LitematicaSchematic schematic)
    {
        List<SavedRule> rules = new ArrayList<>(RuleStore.INSTANCE.getSchematicRules(schematic));
        Set<String> covered = new HashSet<>();
        rules.forEach(rule -> covered.add(rule.sourceItem()));

        for (SavedRule rule : RuleStore.INSTANCE.getGlobalRules())
        {
            if (covered.contains(rule.sourceItem()) == false)
            {
                rules.add(rule);
            }
        }

        return rules;
    }

    public static Summary apply(LitematicaSchematic schematic)
    {
        int applied = 0;
        int blocks = 0;
        int skipped = 0;

        for (SavedRule rule : effectiveRules(schematic))
        {
            Optional<Item> source = rule.resolveSource();
            Optional<Block> target = rule.resolveTarget();

            if (source.isEmpty() || target.isEmpty())
            {
                ++skipped;
                continue;
            }

            ItemReplacementPlan plan = ItemReplacementPlan.forItem(schematic, source.get());

            if (plan.isEmpty())
            {
                continue;
            }

            if (plan.isAllowedTarget(target.get(), rule.keepProperties()) == false)
            {
                ++skipped;
                continue;
            }

            ReplacementResult result = MaterialSwapService.replaceInSchematic(schematic,
                                                                              plan.rulesFor(target.get(), rule.keepProperties()),
                                                                              rule.sourceName(), rule.targetName());

            if (result.isSuccess() && result.totalReplaced() > 0)
            {
                ++applied;
                blocks += result.totalReplaced();
            }
            else if (result.isSuccess() == false)
            {
                ++skipped;
            }
        }

        return new Summary(applied, blocks, skipped);
    }
}
