package de.nils.lmswap.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetCheckBox;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import de.nils.lmswap.config.Configs;
import de.nils.lmswap.core.ItemReplacementPlan;
import de.nils.lmswap.core.MaterialSwapService;
import de.nils.lmswap.core.ReplacementResult;
import de.nils.lmswap.core.ReplacementRule;
import de.nils.lmswap.core.SchematicReplacer;
import de.nils.lmswap.core.Scope;
import de.nils.lmswap.core.SelectionScopes;
import de.nils.lmswap.data.SavedScope;
import de.nils.lmswap.data.RuleStore;
import de.nils.lmswap.data.SavedRule;

/**
 * "Replace material" screen, opened from a material list row.
 * Source on top, searchable target list, options, preview and the replace/cancel buttons at the bottom.
 */
public class GuiMaterialReplace extends GuiListBase<Block, WidgetReplaceTargetEntry, WidgetListReplaceTargets>
                                implements ISelectionListener<Block>
{
    private static final int LIST_Y = 50;
    private static final int BOTTOM_AREA_HEIGHT = 110;
    private static final int RULES_PANEL_WIDTH = 230;
    private static final int RULE_ROW_HEIGHT = 14;

    private final ReplaceContext context;
    private final MaterialListEntry entry;
    private final ItemReplacementPlan plan;
    private final List<Block> targets = new ArrayList<>();
    private final int placementCount;
    private boolean keepProperties = true;
    private boolean rememberForSchematic;
    private boolean rememberGlobal;
    @Nullable private Block selected;

    /** Part B: where to replace */
    private ScopeMode scopeMode = ScopeMode.WHOLE;
    private final List<String> regionNames;
    private final Set<String> selectedRegions;
    private SelectionScopes.Availability selectionAvailability;
    @Nullable private Scope.WorldBoxes selectionBoxes;
    /** Blocks the replacement would hit in the current scope */
    private int scopedCount;

    private enum ScopeMode
    {
        WHOLE("lmswap.gui.button.scope.whole"),
        REGIONS("lmswap.gui.button.scope.regions"),
        SELECTION("lmswap.gui.button.scope.selection");

        final String translationKey;

        ScopeMode(String translationKey)
        {
            this.translationKey = translationKey;
        }
    }

    public GuiMaterialReplace(ReplaceContext context, MaterialListEntry entry)
    {
        super(10, LIST_Y);

        this.context = context;
        this.entry = entry;
        this.plan = ItemReplacementPlan.forItem(context.schematic(), entry.getStack().getItem());
        this.placementCount = context.placements().size();
        this.title = StringUtils.translate("lmswap.gui.title.replace_material");
        this.useTitleHierarchy = false;
        this.collectTargets();

        this.regionNames = new ArrayList<>(context.schematic().getAreas().keySet());
        this.regionNames.sort(String.CASE_INSENSITIVE_ORDER);
        this.selectedRegions = new LinkedHashSet<>(this.regionNames);
        this.refreshSelection();
        this.updateCount();
    }

    private void refreshSelection()
    {
        this.selectionAvailability = SelectionScopes.check(this.context.placement());
        this.selectionBoxes = this.selectionAvailability == SelectionScopes.Availability.AVAILABLE
                              ? SelectionScopes.currentSelection().orElse(null) : null;

        if (this.scopeMode == ScopeMode.SELECTION && this.selectionBoxes == null)
        {
            this.scopeMode = ScopeMode.WHOLE;
        }
    }

    private Scope currentScope()
    {
        return switch (this.scopeMode)
        {
            case WHOLE -> Scope.WHOLE;
            case REGIONS -> new Scope.Regions(this.selectedRegions);
            case SELECTION -> this.selectionBoxes != null ? this.selectionBoxes : Scope.WHOLE;
        };
    }

    private SavedScope currentSavedScope()
    {
        return switch (this.scopeMode)
        {
            case WHOLE -> SavedScope.ALL;
            case REGIONS -> SavedScope.regions(new ArrayList<>(this.selectedRegions));
            case SELECTION -> SavedScope.SELECTION;
        };
    }

    private List<ReplacementRule> currentRules(@Nullable Block target)
    {
        return this.plan.rulesFor(target, this.keepProperties, List.of(), Map.of(), this.currentScope());
    }

    private void updateCount()
    {
        int count = SchematicReplacer.countMatching(this.context.schematic(), this.currentRules(null), this.context.placement());
        this.scopedCount = Math.max(0, count);
    }

    private void setScopeMode(ScopeMode mode)
    {
        this.scopeMode = mode;
        this.refreshSelection();
        this.updateCount();
        this.initGui();
    }

    private void toggleRegion(String region, boolean checked)
    {
        if (checked)
        {
            this.selectedRegions.add(region);
        }
        else
        {
            this.selectedRegions.remove(region);
        }

        this.updateCount();
        this.initGui();
    }

    /**
     * Fills {@link #targets} in place; the list widget holds a reference to it.
     */
    private void collectTargets()
    {
        this.targets.clear();

        for (Block block : BuiltInRegistries.BLOCK)
        {
            if (block.asItem() != Items.AIR && this.plan.isAllowedTarget(block, this.keepProperties))
            {
                this.targets.add(block);
            }
        }

        this.targets.sort(Comparator.comparing(WidgetReplaceTargetEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        this.targets.addFirst(Blocks.AIR);
    }

    private void onKeepPropertiesChanged(boolean keepProperties)
    {
        this.keepProperties = keepProperties;
        this.collectTargets();

        WidgetListReplaceTargets list = this.getListWidget();

        if (this.selected != null && this.targets.contains(this.selected) == false)
        {
            this.selected = null;

            if (list != null)
            {
                list.clearSelection();
            }
        }

        if (list != null)
        {
            list.refreshEntries();
        }

        this.updateCount();
        this.initGui();
    }

    @Override
    protected int getBrowserWidth()
    {
        return this.getScreenWidth() - 20 - RULES_PANEL_WIDTH - 8;
    }

    @Override
    protected int getBrowserHeight()
    {
        return Math.max(60, this.getScreenHeight() - LIST_Y - BOTTOM_AREA_HEIGHT);
    }

    @Override
    protected WidgetListReplaceTargets createListWidget(int listX, int listY)
    {
        return new WidgetListReplaceTargets(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this.targets, this);
    }

    @Override
    public void onSelectionChange(@Nullable Block block)
    {
        this.selected = block;
        this.initGui();
    }

    @Override
    public void initGui()
    {
        super.initGui();

        int y = LIST_Y + this.getBrowserHeight() + 4;

        WidgetCheckBox checkBox = new WidgetCheckBox(12, y, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED,
                                                     StringUtils.translate("lmswap.gui.checkbox.keep_properties"),
                                                     StringUtils.translate("lmswap.gui.checkbox.keep_properties.hover"));
        checkBox.setChecked(this.keepProperties, false);
        checkBox.setListener(cb -> this.onKeepPropertiesChanged(cb.isChecked()));
        this.addWidget(checkBox);

        String hint = StringUtils.translate(this.keepProperties ? "lmswap.gui.label.targets_compatible"
                                                                : "lmswap.gui.label.targets_all",
                                            this.targets.size() - 1);
        this.addLabel(checkBox.getX() + checkBox.getWidth() + 12, y + 1, this.getStringWidth(hint), 10, 0xFFA0A0A0, hint);

        y += 14;
        int x = 12;

        if (RuleStore.INSTANCE.canStoreFor(this.context.schematic()))
        {
            WidgetCheckBox rememberSchematic = new WidgetCheckBox(x, y, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED,
                                                                  StringUtils.translate("lmswap.gui.checkbox.remember_schematic"),
                                                                  StringUtils.translate("lmswap.gui.checkbox.remember_schematic.hover"));
            rememberSchematic.setChecked(this.rememberForSchematic, false);
            rememberSchematic.setListener(cb -> this.rememberForSchematic = cb.isChecked());
            this.addWidget(rememberSchematic);
            x += rememberSchematic.getWidth() + 16;
        }

        WidgetCheckBox rememberGlobal = new WidgetCheckBox(x, y, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED,
                                                           StringUtils.translate("lmswap.gui.checkbox.remember_global"),
                                                           StringUtils.translate("lmswap.gui.checkbox.remember_global.hover"));
        rememberGlobal.setChecked(this.rememberGlobal, false);
        rememberGlobal.setListener(cb -> this.rememberGlobal = cb.isChecked());
        this.addWidget(rememberGlobal);

        int panelY = this.createScopePanel();
        this.createRulesPanel(panelY);

        int buttonY = this.getScreenHeight() - 26;
        ButtonGeneric cancel = new ButtonGeneric(this.getScreenWidth() - 10, buttonY, -1, true, "lmswap.gui.button.cancel");
        this.addButton(cancel, (button, mouseButton) -> this.closeGui(true));

        ButtonGeneric replace = new ButtonGeneric(cancel.getX() - 4, buttonY, -1, true, "lmswap.gui.button.replace_confirm");
        replace.setEnabled(this.selected != null && this.scopedCount > 0);
        this.addButton(replace, (button, mouseButton) -> this.replace());

        // Decision 4: replacements live only in memory until saved as a new schematic
        String notice = StringUtils.translate("lmswap.gui.label.not_saved_notice");
        int noticeWidth = Math.min(this.getStringWidth(notice), replace.getX() - 20);
        this.addLabel(12, buttonY + 6, noticeWidth, 10, 0xFFE0C060, notice);
    }

    /**
     * Part B: "Whole schematic | Selected regions | Current area selection", plus the region check boxes.
     *
     * @return the y position below the panel
     */
    private int createScopePanel()
    {
        int panelX = 10 + this.getBrowserWidth() + 8;
        int width = RULES_PANEL_WIDTH;
        int y = LIST_Y + 4;

        this.addLabel(panelX, y, width, 10, 0xFFFFFFFF, GuiBase.TXT_BOLD + StringUtils.translate("lmswap.gui.label.scope_title"));
        y += 12;

        for (ScopeMode mode : ScopeMode.values())
        {
            boolean active = mode == this.scopeMode;
            String label = (active ? "» " : "") + StringUtils.translate(mode.translationKey);
            ButtonGeneric button = new ButtonGeneric(panelX, y, width, 16, label);
            button.setEnabled(active == false);

            if (mode == ScopeMode.SELECTION && this.selectionAvailability != SelectionScopes.Availability.AVAILABLE)
            {
                button.setEnabled(false);
                button.setHoverStrings("lmswap.gui.scope.selection_unavailable." + this.selectionAvailability.name().toLowerCase(java.util.Locale.ROOT));
            }
            else if (mode == ScopeMode.SELECTION)
            {
                button.setHoverStrings("lmswap.gui.button.scope.selection.hover");
            }

            this.addButton(button, (btn, mouseButton) -> this.setScopeMode(mode));
            y += 17;
        }

        if (this.scopeMode == ScopeMode.REGIONS)
        {
            int maxRows = 6;
            int shown = 0;

            for (String region : this.regionNames)
            {
                if (shown == maxRows)
                {
                    this.addLabel(panelX + 4, y, width, 10, 0xFFA0A0A0,
                                  StringUtils.translate("lmswap.gui.label.rules_more", this.regionNames.size() - shown));
                    y += 12;
                    break;
                }

                String label = StringUtils.clampTextToRenderLength(region, width - 20, LeftRight.RIGHT, "...");
                WidgetCheckBox box = new WidgetCheckBox(panelX + 4, y, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED, label);
                box.setChecked(this.selectedRegions.contains(region), false);
                box.setListener(cb -> this.toggleRegion(region, cb.isChecked()));
                this.addWidget(box);
                y += 12;
                ++shown;
            }
        }

        return y + 8;
    }

    /**
     * Saved rules of this schematic and the global rules, each with a delete button.
     */
    private void createRulesPanel(int startY)
    {
        int panelX = 10 + this.getBrowserWidth() + 8;
        int y = startY;
        int width = RULES_PANEL_WIDTH;
        int maxY = LIST_Y + this.getBrowserHeight() - RULE_ROW_HEIGHT;

        String title = StringUtils.translate("lmswap.gui.label.rules_title");
        this.addLabel(panelX, y, width, 10, 0xFFFFFFFF, GuiBase.TXT_BOLD + title);
        y += 12;

        String status = StringUtils.translate(Configs.AUTO_APPLY_RULES.getBooleanValue() ? "lmswap.gui.label.rules_auto_on"
                                                                                          : "lmswap.gui.label.rules_auto_off");
        this.addLabel(panelX, y, width, 10, 0xFFA0A0A0, StringUtils.clampTextToRenderLength(status, width, LeftRight.RIGHT, "..."));
        y += 14;

        List<SavedRule> schematicRules = RuleStore.INSTANCE.getSchematicRules(this.context.schematic());
        List<SavedRule> globalRules = RuleStore.INSTANCE.getGlobalRules();
        int total = schematicRules.size() + globalRules.size();
        int shown = 0;

        if (total == 0)
        {
            this.addLabel(panelX, y, width, 10, 0xFFA0A0A0, StringUtils.translate("lmswap.gui.label.rules_none"));
            return;
        }

        for (int i = 0; i < total && y <= maxY; ++i, ++shown)
        {
            boolean global = i >= schematicRules.size();
            SavedRule rule = global ? globalRules.get(i - schematicRules.size()) : schematicRules.get(i);

            ButtonGeneric delete = new ButtonGeneric(panelX, y - 2, 14, 12, "x",
                                                     StringUtils.translate("lmswap.gui.button.rule_delete.hover"));
            this.addButton(delete, (button, mouseButton) -> this.deleteRule(rule, global));

            String prefix = global ? GuiBase.TXT_GOLD + StringUtils.translate("lmswap.gui.label.rule_global") + " " + GuiBase.TXT_RST : "";
            String suffix = rule.keepProperties() ? "" : " " + StringUtils.translate("lmswap.gui.label.rule_reset");
            String text = prefix + rule.sourceName() + " → " + rule.targetName() + suffix;
            this.addLabel(panelX + 18, y, width - 18, 10, 0xFFFFFFFF,
                          StringUtils.clampTextToRenderLength(text, width - 18, LeftRight.RIGHT, "..."));
            y += RULE_ROW_HEIGHT;
        }

        if (shown < total)
        {
            this.addLabel(panelX + 18, y, width - 18, 10, 0xFFA0A0A0,
                          StringUtils.translate("lmswap.gui.label.rules_more", total - shown));
        }
    }

    private void deleteRule(SavedRule rule, boolean global)
    {
        if (global)
        {
            RuleStore.INSTANCE.removeGlobal(rule);
        }
        else
        {
            RuleStore.INSTANCE.removeForSchematic(this.context.schematic(), rule);
        }

        this.initGui();
    }

    private void rememberRule()
    {
        if (this.selected == null || (this.rememberForSchematic == false && this.rememberGlobal == false))
        {
            return;
        }

        SavedRule rule = SavedRule.of(this.entry.getStack().getItem(), this.selected, this.keepProperties)
                                  .withFilter(List.of(), Map.of(), this.currentSavedScope());

        if (this.rememberForSchematic)
        {
            RuleStore.INSTANCE.putForSchematic(this.context.schematic(), rule);
        }

        if (this.rememberGlobal)
        {
            RuleStore.INSTANCE.putGlobal(rule);
        }
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        this.drawSource(ctx);
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        this.drawPreview(ctx);
    }

    private void drawSource(GuiContext ctx)
    {
        int x = 12;
        int y = 24;

        RenderUtils.drawRect(ctx, x, y, 16, 16, 0x20FFFFFF);
        ctx.renderItem(this.entry.getStack(), x, y);

        String source = StringUtils.translate("lmswap.gui.label.source", this.entry.getStack().getHoverName().getString(),
                                              this.plan.getBlockCount());
        this.drawString(ctx, source, x + 22, y + 4, 0xFFFFFFFF);

        if (this.placementCount > 1)
        {
            String shared = StringUtils.translate("lmswap.warning.shared_schematic", this.placementCount);
            this.drawString(ctx, GuiBase.TXT_GOLD + shared, x + 22 + this.getStringWidth(source) + 12, y + 4, 0xFFFFFFFF);
        }
    }

    private void drawPreview(GuiContext ctx)
    {
        int x = 12;
        int y = LIST_Y + this.getBrowserHeight() + 34;

        if (this.plan.getBlockCount() == 0)
        {
            this.drawString(ctx, GuiBase.TXT_RED + StringUtils.translate("lmswap.error.nothing_to_replace"), x, y, 0xFFFFFFFF);
            return;
        }

        if (this.scopedCount == 0)
        {
            this.drawString(ctx, GuiBase.TXT_RED + StringUtils.translate("lmswap.gui.label.scope_empty"), x, y, 0xFFFFFFFF);
            return;
        }

        if (this.selected == null)
        {
            this.drawString(ctx, GuiBase.TXT_GRAY + StringUtils.translate("lmswap.gui.label.select_target"), x, y, 0xFFFFFFFF);
            return;
        }

        String preview = StringUtils.translate("lmswap.gui.label.preview", this.scopedCount,
                                               this.entry.getStack().getHoverName().getString(),
                                               WidgetReplaceTargetEntry.displayName(this.selected));
        this.drawString(ctx, preview, x, y, 0xFFFFFFFF);

        List<ReplacementResult.Message> warnings = new ArrayList<>(this.plan.previewWarnings(this.selected, this.keepProperties));

        if (this.scopeMode == ScopeMode.SELECTION && this.placementCount > 1)
        {
            warnings.add(ReplacementResult.Message.of("lmswap.warning.selection_shared", this.placementCount));
        }

        for (ReplacementResult.Message warning : warnings)
        {
            y += 11;
            this.drawString(ctx, GuiBase.TXT_GOLD + StringUtils.translate(warning.translationKey(), warning.args().toArray()),
                            x, y, 0xFFFFFFFF);
        }
    }

    private void replace()
    {
        if (this.selected == null)
        {
            return;
        }

        if (this.context.isStillLoaded() == false)
        {
            this.addMessage(MessageType.ERROR, "lmswap.error.schematic_not_loaded");
            return;
        }

        ReplacementResult result = MaterialSwapService.replaceInSchematic(this.context.schematic(),
                                                                          this.currentRules(this.selected),
                                                                          this.context.placement(),
                                                                          this.entry.getStack().getHoverName().getString(),
                                                                          WidgetReplaceTargetEntry.displayName(this.selected));
        Screen parent = this.getParent();

        if (result.isSuccess() && result.change() != null)
        {
            this.context.refreshMaterialList();
        }

        if (result.isSuccess())
        {
            this.rememberRule();
        }

        if (parent instanceof GuiBase gui)
        {
            if (result.isSuccess() == false)
            {
                gui.addMessage(MessageType.ERROR, result.error().translationKey(), result.error().args().toArray());
            }
            else
            {
                gui.addMessage(MessageType.SUCCESS, "lmswap.info.replaced_total", result.totalReplaced(),
                               this.entry.getStack().getHoverName().getString());

                for (ReplacementResult.Message warning : result.warnings())
                {
                    gui.addMessage(MessageType.WARNING, warning.translationKey(), warning.args().toArray());
                }
            }
        }

        this.closeGui(true);
    }
}
