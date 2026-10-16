package de.nils.lmswap.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import de.nils.lmswap.config.Configs;
import de.nils.lmswap.core.ItemReplacementPlan;
import de.nils.lmswap.core.MaterialSwapService;
import de.nils.lmswap.core.PropertyCondition;
import de.nils.lmswap.core.ReplacementResult;
import de.nils.lmswap.core.ReplacementRule;
import de.nils.lmswap.core.Scope;
import de.nils.lmswap.core.SelectionScopes;
import de.nils.lmswap.data.RuleStore;
import de.nils.lmswap.data.SavedRule;
import de.nils.lmswap.data.SavedScope;

/**
 * "Replace material" screen, opened from a material list row.
 * <ul>
 *   <li>Top: source material with icon and count.</li>
 *   <li>Left: searchable target list; below it the options, the collapsible "Filter" and "Fixed target properties"
 *       sections (part C), the live preview and the replace/cancel buttons.</li>
 *   <li>Right: area switcher (part B) and the saved rules.</li>
 * </ul>
 * Edit mode (part D): with a rule consumer the screen edits a batch rule instead of replacing. The button becomes
 * "Accept", the area switcher offers "Batch area" (the rule uses the batch scope), and an existing rule is pre-filled.
 * <p>
 * Counting: the blocks per source state in the chosen area are counted once (one pass over the schematic) whenever
 * the area changes. Filter changes only sum those counts, so the preview updates instantly even for large schematics.
 */
public class GuiMaterialReplace extends GuiListBase<Block, WidgetReplaceTargetEntry, WidgetListReplaceTargets>
                                implements ISelectionListener<Block>
{
    private static final int LIST_Y = 50;
    /** keep-properties row, remember row, section toggles, preview (4 lines), buttons */
    private static final int FIXED_BOTTOM_HEIGHT = 14 + 16 + 18 + 50 + 30;
    private static final int RULES_PANEL_WIDTH = 230;
    private static final int RULE_ROW_HEIGHT = 14;
    private static final int CHIP_HEIGHT = 14;
    private static final int CHIP_ROW_HEIGHT = 15;
    private static final int SECTION_LABEL_WIDTH = 90;

    private final ReplaceContext context;
    private final MaterialListEntry entry;
    private final ItemReplacementPlan plan;
    private final List<Block> targets = new ArrayList<>();
    private final int placementCount;
    private boolean keepProperties = true;
    private boolean rememberForSchematic;
    private boolean rememberGlobal;
    @Nullable private Block selected;

    // Part D: edit mode for batch rules
    @Nullable private final Consumer<SavedRule> ruleConsumer;
    private final Scope inheritedScope;
    @Nullable private Block pendingPreselect;

    // Part B: where to replace
    private ScopeMode scopeMode = ScopeMode.WHOLE;
    private final List<String> regionNames;
    private final Set<String> selectedRegions;
    private SelectionScopes.Availability selectionAvailability;
    @Nullable private Scope.WorldBoxes selectionBoxes;

    // Part C: filter and overrides
    private final Map<String, List<String>> sourceProperties;
    /** property -> selected values; all values selected = no condition for that property */
    private final Map<String, Set<String>> filterSelection = new LinkedHashMap<>();
    /** fixed target properties (only properties/values the selected target has) */
    private final Map<String, String> overrides = new LinkedHashMap<>();
    private boolean filterExpanded;
    private boolean overridesExpanded;
    private Map<BlockState, Integer> stateCounts = Map.of();
    private int previewY;

    private enum ScopeMode
    {
        INHERIT("lmswap.gui.button.scope.inherit"),
        WHOLE("lmswap.gui.button.scope.whole"),
        REGIONS("lmswap.gui.button.scope.regions"),
        SELECTION("lmswap.gui.button.scope.selection");

        final String translationKey;

        ScopeMode(String translationKey)
        {
            this.translationKey = translationKey;
        }
    }

    /** A small toggle button in a flowing row */
    private record Chip(String text, Runnable action)
    {
    }

    public GuiMaterialReplace(ReplaceContext context, MaterialListEntry entry)
    {
        this(context, entry, null, null, Scope.WHOLE);
    }

    /**
     * @param initial        rule to pre-fill (edit mode), may be null
     * @param ruleConsumer   non-null = edit mode: "Accept" hands the rule to the consumer instead of replacing
     * @param inheritedScope the batch scope, used for counting when the rule uses "Batch area"
     */
    public GuiMaterialReplace(ReplaceContext context, MaterialListEntry entry, @Nullable SavedRule initial,
                              @Nullable Consumer<SavedRule> ruleConsumer, Scope inheritedScope)
    {
        super(10, LIST_Y);

        this.ruleConsumer = ruleConsumer;
        this.inheritedScope = inheritedScope;
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

        this.sourceProperties = this.plan.sourceProperties();
        this.sourceProperties.forEach((property, values) -> this.filterSelection.put(property, new LinkedHashSet<>(values)));

        if (this.isEditMode())
        {
            this.scopeMode = ScopeMode.INHERIT;
        }

        if (initial != null)
        {
            this.applyInitial(initial);
        }

        this.refreshSelection();
        this.recount();
    }

    private boolean isEditMode()
    {
        return this.ruleConsumer != null;
    }

    /** Pre-fills the screen from an existing rule (edit mode) */
    private void applyInitial(SavedRule rule)
    {
        if (this.keepProperties != rule.keepProperties())
        {
            this.keepProperties = rule.keepProperties();
            this.collectTargets();
        }

        rule.resolveTarget().filter(this.targets::contains).ifPresent(target -> {
            this.selected = target;
            this.pendingPreselect = target;
        });

        rule.conditions().forEach((property, values) -> {
            Set<String> selection = this.filterSelection.get(property);

            if (selection != null)
            {
                selection.retainAll(values);
            }
        });

        this.overrides.putAll(rule.overrides());
        this.pruneOverrides();

        switch (rule.scope().type())
        {
            case ALL -> this.scopeMode = ScopeMode.WHOLE;
            case INHERIT -> this.scopeMode = this.isEditMode() ? ScopeMode.INHERIT : ScopeMode.WHOLE;
            case SELECTION -> this.scopeMode = ScopeMode.SELECTION;
            case REGIONS -> {
                this.scopeMode = ScopeMode.REGIONS;
                this.selectedRegions.retainAll(rule.scope().regions());
            }
        }
    }

    // ---- targets ----

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
            this.overrides.clear();

            if (list != null)
            {
                list.clearSelection();
            }
        }

        if (list != null)
        {
            list.refreshEntries();
        }

        this.initGui();
    }

    @Override
    public void onSelectionChange(@Nullable Block block)
    {
        this.selected = block;
        this.pruneOverrides();
        this.initGui();
    }

    // ---- scope (part B) ----

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
            case INHERIT -> this.inheritedScope;
            case WHOLE -> Scope.WHOLE;
            case REGIONS -> new Scope.Regions(this.selectedRegions);
            case SELECTION -> this.selectionBoxes != null ? this.selectionBoxes : Scope.WHOLE;
        };
    }

    private SavedScope currentSavedScope()
    {
        return switch (this.scopeMode)
        {
            case INHERIT -> SavedScope.INHERIT;
            case WHOLE -> SavedScope.ALL;
            case REGIONS -> SavedScope.regions(new ArrayList<>(this.selectedRegions));
            case SELECTION -> SavedScope.SELECTION;
        };
    }

    private void setScopeMode(ScopeMode mode)
    {
        this.scopeMode = mode;
        this.refreshSelection();
        this.recount();
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

        this.recount();
        this.initGui();
    }

    /** One pass over the schematic: blocks per source state in the current scope */
    private void recount()
    {
        this.stateCounts = this.plan.stateCounts(this.currentScope(), this.context.placement());
    }

    // ---- filter and overrides (part C) ----

    private List<PropertyCondition> currentConditions()
    {
        List<PropertyCondition> conditions = new ArrayList<>();

        this.filterSelection.forEach((property, values) -> {
            if (values.size() != this.sourceProperties.get(property).size())
            {
                conditions.add(new PropertyCondition(property, values));
            }
        });

        return conditions;
    }

    @Nullable
    private ReplacementResult.Message filterError()
    {
        for (Map.Entry<String, Set<String>> e : this.filterSelection.entrySet())
        {
            if (e.getValue().isEmpty())
            {
                return ReplacementResult.Message.of("lmswap.error.filter_no_values", e.getKey());
            }
        }

        return this.plan.validateConditions(this.currentConditions());
    }

    private int filteredCount()
    {
        return this.filterError() != null ? 0 : ItemReplacementPlan.filteredCount(this.stateCounts, this.currentConditions());
    }

    private void toggleFilterValue(String property, String value)
    {
        Set<String> values = this.filterSelection.get(property);

        if (values.remove(value) == false)
        {
            values.add(value);
        }

        this.initGui();
    }

    private void setFilterAll(String property, boolean all)
    {
        Set<String> values = this.filterSelection.get(property);
        values.clear();

        if (all)
        {
            values.addAll(this.sourceProperties.get(property));
        }

        this.initGui();
    }

    private void setOverride(String property, @Nullable String value)
    {
        if (value == null)
        {
            this.overrides.remove(property);
        }
        else
        {
            this.overrides.put(property, value);
        }

        this.initGui();
    }

    /** Keeps only overrides the selected target block actually has */
    private void pruneOverrides()
    {
        Block target = this.selected;

        if (target == null || target.defaultBlockState().isAir())
        {
            this.overrides.clear();
            return;
        }

        this.overrides.entrySet().removeIf(e -> {
            Property<?> prop = target.getStateDefinition().getProperty(e.getKey());
            return prop == null || prop.getValue(e.getValue()).isEmpty();
        });
    }

    private Map<String, String> effectiveOverrides()
    {
        return this.selected == null || this.selected.defaultBlockState().isAir() ? Map.of() : Map.copyOf(this.overrides);
    }

    private List<ReplacementRule> currentRules(Block target)
    {
        return this.plan.rulesFor(target, this.keepProperties, this.currentConditions(), this.effectiveOverrides(), this.currentScope());
    }

    // ---- layout ----

    @Override
    protected int getBrowserWidth()
    {
        return this.getScreenWidth() - 20 - RULES_PANEL_WIDTH - 8;
    }

    @Override
    protected int getBrowserHeight()
    {
        return Math.max(60, this.getScreenHeight() - LIST_Y - FIXED_BOTTOM_HEIGHT - this.layoutSections(0, false));
    }

    @Override
    protected WidgetListReplaceTargets createListWidget(int listX, int listY)
    {
        return new WidgetListReplaceTargets(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this.targets, this);
    }

    @Override
    public void initGui()
    {
        super.initGui();

        if (this.pendingPreselect != null && this.getListWidget() != null)
        {
            this.getListWidget().setLastSelectedEntry(this.pendingPreselect, this.targets.indexOf(this.pendingPreselect));
            this.pendingPreselect = null;
        }

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

        if (this.isEditMode())
        {
            // Batch rules are saved with the rule set, not individually
        }
        else if (RuleStore.INSTANCE.canStoreFor(this.context.schematic()))
        {
            WidgetCheckBox rememberSchematic = new WidgetCheckBox(x, y, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED,
                                                                  StringUtils.translate("lmswap.gui.checkbox.remember_schematic"),
                                                                  StringUtils.translate("lmswap.gui.checkbox.remember_schematic.hover"));
            rememberSchematic.setChecked(this.rememberForSchematic, false);
            rememberSchematic.setListener(cb -> this.rememberForSchematic = cb.isChecked());
            this.addWidget(rememberSchematic);
            x += rememberSchematic.getWidth() + 16;
        }

        if (this.isEditMode() == false)
        {
            WidgetCheckBox rememberGlobal = new WidgetCheckBox(x, y, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED,
                                                               StringUtils.translate("lmswap.gui.checkbox.remember_global"),
                                                               StringUtils.translate("lmswap.gui.checkbox.remember_global.hover"));
            rememberGlobal.setChecked(this.rememberGlobal, false);
            rememberGlobal.setListener(cb -> this.rememberGlobal = cb.isChecked());
            this.addWidget(rememberGlobal);
        }

        // Section toggles
        y += 16;
        int activeFilters = this.currentConditions().size();
        String filterLabel = (this.filterExpanded ? "▾ " : "▸ ") + StringUtils.translate("lmswap.gui.button.section_filter", activeFilters);
        ButtonGeneric filterToggle = new ButtonGeneric(12, y, this.getStringWidth(filterLabel) + 10, 16, filterLabel,
                                                       StringUtils.translate("lmswap.gui.button.section_filter.hover"));
        this.addButton(filterToggle, (b, m) -> { this.filterExpanded = ! this.filterExpanded; this.initGui(); });

        String overrideLabel = (this.overridesExpanded ? "▾ " : "▸ ")
                               + StringUtils.translate("lmswap.gui.button.section_overrides", this.effectiveOverrides().size());
        ButtonGeneric overrideToggle = new ButtonGeneric(filterToggle.getX() + filterToggle.getWidth() + 4, y,
                                                         this.getStringWidth(overrideLabel) + 10, 16, overrideLabel,
                                                         StringUtils.translate("lmswap.gui.button.section_overrides.hover"));
        this.addButton(overrideToggle, (b, m) -> { this.overridesExpanded = ! this.overridesExpanded; this.initGui(); });

        y += 18;
        y = this.layoutSections(y, true);
        this.previewY = y + 2;

        int panelY = this.createScopePanel();
        this.createRulesPanel(panelY);

        int buttonY = this.getScreenHeight() - 26;
        ButtonGeneric cancel = new ButtonGeneric(this.getScreenWidth() - 10, buttonY, -1, true, "lmswap.gui.button.cancel");
        this.addButton(cancel, (button, mouseButton) -> this.closeGui(true));

        ButtonGeneric replace = new ButtonGeneric(cancel.getX() - 4, buttonY, -1, true,
                                                  this.isEditMode() ? "lmswap.gui.button.accept_rule" : "lmswap.gui.button.replace_confirm");
        replace.setEnabled(this.selected != null && (this.isEditMode() ? this.filterError() == null : this.filteredCount() > 0));
        this.addButton(replace, (button, mouseButton) -> this.replace());

        // Decision 4: replacements live only in memory until saved as a new schematic
        String notice = StringUtils.translate("lmswap.gui.label.not_saved_notice");
        int noticeWidth = Math.min(this.getStringWidth(notice), replace.getX() - 20);
        this.addLabel(12, buttonY + 6, noticeWidth, 10, 0xFFE0C060, notice);
    }

    /**
     * Lays out the expanded filter and override sections. With {@code create == false} it only measures.
     *
     * @return the y position below the sections (the height, when called with y = 0)
     */
    private int layoutSections(int y, boolean create)
    {
        if (this.filterExpanded)
        {
            if (this.sourceProperties.isEmpty())
            {
                y = this.textRow(y, StringUtils.translate("lmswap.gui.label.filter_no_properties"), create);
            }

            for (Map.Entry<String, List<String>> e : this.sourceProperties.entrySet())
            {
                String property = e.getKey();
                Set<String> selectedValues = this.filterSelection.get(property);
                List<Chip> chips = new ArrayList<>();
                boolean all = selectedValues.size() == e.getValue().size();

                chips.add(new Chip(StringUtils.translate(all ? "lmswap.gui.button.filter_none" : "lmswap.gui.button.filter_all"),
                                   () -> this.setFilterAll(property, all == false)));

                for (String value : e.getValue())
                {
                    int count = ItemReplacementPlan.valueCount(this.stateCounts, property, value);
                    boolean on = selectedValues.contains(value);
                    String text = (on ? GuiBase.TXT_GREEN + "✔ " : GuiBase.TXT_GRAY) + value + " (" + count + ")";
                    chips.add(new Chip(text, () -> this.toggleFilterValue(property, value)));
                }

                y = this.chipRow(y, property + ":", chips, create);
            }
        }

        if (this.overridesExpanded)
        {
            Block target = this.selected;

            if (target == null || target.defaultBlockState().isAir())
            {
                y = this.textRow(y, StringUtils.translate("lmswap.gui.label.overrides_select_target"), create);
            }
            else if (target.getStateDefinition().getProperties().isEmpty())
            {
                y = this.textRow(y, StringUtils.translate("lmswap.gui.label.overrides_no_properties"), create);
            }
            else
            {
                for (Property<?> prop : target.getStateDefinition().getProperties())
                {
                    String property = prop.getName();
                    String current = this.overrides.get(property);
                    List<Chip> chips = new ArrayList<>();
                    chips.add(new Chip((current == null ? GuiBase.TXT_GREEN + "✔ " : GuiBase.TXT_GRAY)
                                       + StringUtils.translate("lmswap.gui.button.override_keep"), () -> this.setOverride(property, null)));

                    for (String value : valueNames(prop))
                    {
                        boolean on = value.equals(current);
                        chips.add(new Chip((on ? GuiBase.TXT_GREEN + "✔ " : GuiBase.TXT_GRAY) + value,
                                           () -> this.setOverride(property, value)));
                    }

                    y = this.chipRow(y, property + ":", chips, create);
                }
            }
        }

        return y;
    }

    private static <T extends Comparable<T>> List<String> valueNames(Property<T> prop)
    {
        return prop.getPossibleValues().stream().map(prop::getName).toList();
    }

    private int textRow(int y, String text, boolean create)
    {
        if (create)
        {
            this.addLabel(14, y + 3, this.getStringWidth(text), 10, 0xFFA0A0A0, text);
        }

        return y + CHIP_ROW_HEIGHT;
    }

    private int chipRow(int y, String label, List<Chip> chips, boolean create)
    {
        int x0 = 14 + SECTION_LABEL_WIDTH;
        int maxX = this.getScreenWidth() - 12;
        int x = x0;

        if (create)
        {
            this.addLabel(14, y + 3, SECTION_LABEL_WIDTH - 4, 10, 0xFFFFFFFF,
                          StringUtils.clampTextToRenderLength(label, SECTION_LABEL_WIDTH - 4, LeftRight.RIGHT, "..."));
        }

        for (Chip chip : chips)
        {
            int w = this.getStringWidth(chip.text()) + 8;

            if (x + w > maxX && x > x0)
            {
                x = x0;
                y += CHIP_ROW_HEIGHT;
            }

            if (create)
            {
                ButtonGeneric button = new ButtonGeneric(x, y, w, CHIP_HEIGHT, chip.text());
                this.addButton(button, (b, m) -> chip.action().run());
            }

            x += w + 2;
        }

        return y + CHIP_ROW_HEIGHT + 2;
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
            if (mode == ScopeMode.INHERIT && this.isEditMode() == false)
            {
                continue;
            }

            boolean active = mode == this.scopeMode;
            String label = (active ? "» " : "") + StringUtils.translate(mode.translationKey);
            ButtonGeneric button = new ButtonGeneric(panelX, y, width, 16, label);
            button.setEnabled(active == false);

            if (mode == ScopeMode.SELECTION && this.selectionAvailability != SelectionScopes.Availability.AVAILABLE)
            {
                button.setEnabled(false);
                button.setHoverStrings("lmswap.gui.scope.selection_unavailable." + this.selectionAvailability.name().toLowerCase(Locale.ROOT));
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

            String text = RuleTexts.describe(rule, global);
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
                                  .withFilter(this.currentConditions(), this.effectiveOverrides(), this.currentSavedScope());

        if (this.rememberForSchematic)
        {
            RuleStore.INSTANCE.putForSchematic(this.context.schematic(), rule);
        }

        if (this.rememberGlobal)
        {
            RuleStore.INSTANCE.putGlobal(rule);
        }
    }

    // ---- drawing ----

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
        int y = this.previewY;

        if (this.plan.getBlockCount() == 0)
        {
            this.drawString(ctx, GuiBase.TXT_RED + StringUtils.translate("lmswap.error.nothing_to_replace"), x, y, 0xFFFFFFFF);
            return;
        }

        ReplacementResult.Message error = this.filterError();

        if (error != null)
        {
            this.drawString(ctx, GuiBase.TXT_RED + StringUtils.translate(error.translationKey(), error.args().toArray()), x, y, 0xFFFFFFFF);
            return;
        }

        int count = this.filteredCount();

        if (count == 0)
        {
            this.drawString(ctx, GuiBase.TXT_RED + StringUtils.translate("lmswap.gui.label.scope_empty"), x, y, 0xFFFFFFFF);
            return;
        }

        if (this.selected == null)
        {
            this.drawString(ctx, GuiBase.TXT_GRAY + StringUtils.translate("lmswap.gui.label.select_target_count", count), x, y, 0xFFFFFFFF);
            return;
        }

        String preview = StringUtils.translate("lmswap.gui.label.preview", count,
                                               this.entry.getStack().getHoverName().getString(),
                                               WidgetReplaceTargetEntry.displayName(this.selected));
        this.drawString(ctx, preview, x, y, 0xFFFFFFFF);

        List<ReplacementResult.Message> warnings = new ArrayList<>(this.plan.previewWarnings(this.selected, this.keepProperties,
                                                                                             this.currentConditions(),
                                                                                             this.effectiveOverrides()));

        if (this.scopeMode == ScopeMode.SELECTION && this.placementCount > 1)
        {
            warnings.add(ReplacementResult.Message.of("lmswap.warning.selection_shared", this.placementCount));
        }

        int maxLines = 3;

        for (int i = 0; i < warnings.size() && i < maxLines; ++i)
        {
            ReplacementResult.Message warning = warnings.get(i);
            y += 11;
            this.drawString(ctx, GuiBase.TXT_GOLD + StringUtils.translate(warning.translationKey(), warning.args().toArray()),
                            x, y, 0xFFFFFFFF);
        }

        if (warnings.size() > maxLines)
        {
            y += 11;
            this.drawString(ctx, GuiBase.TXT_GOLD + StringUtils.translate("lmswap.gui.label.rules_more", warnings.size() - maxLines),
                            x, y, 0xFFFFFFFF);
        }
    }

    // ---- action ----

    private void replace()
    {
        if (this.selected == null || this.filterError() != null)
        {
            return;
        }

        if (this.ruleConsumer != null)
        {
            this.ruleConsumer.accept(SavedRule.of(this.entry.getStack().getItem(), this.selected, this.keepProperties)
                                              .withFilter(this.currentConditions(), this.effectiveOverrides(), this.currentSavedScope()));
            this.closeGui(true);
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
