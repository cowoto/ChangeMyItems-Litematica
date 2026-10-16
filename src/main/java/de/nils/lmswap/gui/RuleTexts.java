package de.nils.lmswap.gui;

import java.util.List;
import java.util.Map;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.StringUtils;

import de.nils.lmswap.data.SavedRule;
import de.nils.lmswap.data.SavedScope;

/**
 * One-line descriptions of saved rules for lists (rules panel, batch screen).
 */
public final class RuleTexts
{
    private RuleTexts()
    {
    }

    public static String describe(SavedRule rule, boolean global)
    {
        StringBuilder sb = new StringBuilder();

        if (global)
        {
            sb.append(GuiBase.TXT_GOLD).append(StringUtils.translate("lmswap.gui.label.rule_global")).append(' ').append(GuiBase.TXT_RST);
        }

        sb.append(rule.sourceName());

        if (rule.conditions().isEmpty() == false)
        {
            sb.append(GuiBase.TXT_GRAY).append(" [").append(conditions(rule.conditions())).append(']').append(GuiBase.TXT_RST);
        }

        sb.append(" → ").append(rule.targetName());

        if (rule.overrides().isEmpty() == false)
        {
            sb.append(GuiBase.TXT_GRAY).append(" {").append(overrides(rule.overrides())).append('}').append(GuiBase.TXT_RST);
        }

        if (rule.keepProperties() == false)
        {
            sb.append(' ').append(StringUtils.translate("lmswap.gui.label.rule_reset"));
        }

        if (rule.scope().type() != SavedScope.Type.ALL && rule.scope().type() != SavedScope.Type.INHERIT)
        {
            sb.append(GuiBase.TXT_AQUA).append(' ').append(scope(rule.scope())).append(GuiBase.TXT_RST);
        }

        return sb.toString();
    }

    public static String conditions(Map<String, List<String>> conditions)
    {
        StringBuilder sb = new StringBuilder();
        conditions.forEach((property, values) -> {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }

            sb.append(property).append('=').append(String.join("|", values));
        });
        return sb.toString();
    }

    public static String overrides(Map<String, String> overrides)
    {
        StringBuilder sb = new StringBuilder();
        overrides.forEach((property, value) -> {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }

            sb.append(property).append('=').append(value);
        });
        return sb.toString();
    }

    public static String scope(SavedScope scope)
    {
        return switch (scope.type())
        {
            case ALL -> StringUtils.translate("lmswap.gui.button.scope.whole");
            case INHERIT -> StringUtils.translate("lmswap.gui.button.scope.inherit");
            case REGIONS -> StringUtils.translate("lmswap.gui.label.scope_regions_short", String.join(", ", scope.regions()));
            case SELECTION -> StringUtils.translate("lmswap.gui.button.scope.selection");
        };
    }
}
