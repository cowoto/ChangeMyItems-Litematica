package de.nils.lmswap.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetCheckBox;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.input.ScanCodes;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import de.nils.lmswap.core.BatchReplacer;
import de.nils.lmswap.core.BatchRule;
import de.nils.lmswap.core.MaterialResolver;
import de.nils.lmswap.core.MaterialSwapService;
import de.nils.lmswap.core.ReplacementResult;
import de.nils.lmswap.core.ReplacementResult.Message;
import de.nils.lmswap.core.Scope;
import de.nils.lmswap.core.SelectionScopes;
import de.nils.lmswap.data.RuleSet;
import de.nils.lmswap.data.RuleSet.ChainMode;
import de.nils.lmswap.data.RuleStore;
import de.nils.lmswap.data.SavedRule;
import de.nils.lmswap.data.SavedScope;

/**
 * Batch replacement (part D), opened from the material list.
 * <ul>
 *   <li>Left: the rules in execution order (first matching rule wins), each with ↑ ↓ ✎ x and its preview numbers;
 *       Shift+↑/↓ moves the selected rule.</li>
 *   <li>Right: batch area (shared by all rules unless a rule overrides it), chain mode, add rule, rule sets and saved
 *       rules as templates.</li>
 *   <li>Bottom: total, material list delta, warnings (errors, chains, overlaps), execute/cancel.</li>
 * </ul>
 * The preview is recomputed on every change ({@link BatchReplacer#analyze}); execution is one undo step with one rebuild.
 */
public class GuiBatchReplace extends GuiListBase<GuiBatchReplace.BatchRow, GuiBatchReplace.RowWidget, GuiBatchReplace.RowList>
                             implements ISelectionListener<GuiBatchReplace.BatchRow>
{
    private static final int LIST_Y = 44;
    private static final int PANEL_WIDTH = 230;
    private static final int BOTTOM_HEIGHT = 92;
    private static final int MAX_WARNING_LINES = 3;

    private final ReplaceContext context;
    private final List<SavedRule> rules = new ArrayList<>();
    private int selectedIndex = -1;

    private SavedScope.Type scopeType = SavedScope.Type.ALL;
    private final List<String> regionNames;
    private final Set<String> selectedRegions;
    private SelectionScopes.Availability selectionAvailability;
    private ChainMode chainMode = ChainMode.ORIGINAL;

    // Preview state
    private final List<BatchRules.Converted> converted = new ArrayList<>();
    @Nullable private BatchReplacer.Analysis analysis;
    /** analysis index per rule index (-1 = not part of the analysis because it could not be converted) */
    private int[] analysisIndex = new int[0];
    @Nullable private Message batchError;

    public record BatchRow(int index, SavedRule rule)
    {
    }

    public GuiBatchReplace(ReplaceContext context)
    {
        super(10, LIST_Y);

        this.context = context;
        this.title = StringUtils.translate("lmswap.gui.title.batch", context.schematic().getMetadata().getName());
        this.useTitleHierarchy = false;
        this.regionNames = new ArrayList<>(context.schematic().getAreas().keySet());
        this.regionNames.sort(String.CASE_INSENSITIVE_ORDER);
        this.selectedRegions = new LinkedHashSet<>(this.regionNames);
        this.selectionAvailability = SelectionScopes.check(context.placement());
        this.reanalyze();
    }

    // ---- model ----

    private SavedScope batchSavedScope()
    {
        return switch (this.scopeType)
        {
            case REGIONS -> SavedScope.regions(new ArrayList<>(this.selectedRegions));
            case SELECTION -> SavedScope.SELECTION;
            default -> SavedScope.ALL;
        };
    }

    void addRule(SavedRule rule)
    {
        this.rules.add(rule);
        this.selectedIndex = this.rules.size() - 1;
        this.changed();
    }

    void replaceRule(int index, SavedRule rule)
    {
        this.rules.set(index, rule);
        this.changed();
    }

    void removeRule(int index)
    {
        this.rules.remove(index);
        this.selectedIndex = Math.min(this.selectedIndex, this.rules.size() - 1);
        this.changed();
    }

    void moveRule(int index, int delta)
    {
        int target = index + delta;

        if (index < 0 || index >= this.rules.size() || target < 0 || target >= this.rules.size())
        {
            return;
        }

        SavedRule rule = this.rules.remove(index);
        this.rules.add(target, rule);
        this.selectedIndex = target;
        this.changed();
    }

    private void changed()
    {
        this.reanalyze();

        if (this.getListWidget() != null)
        {
            this.getListWidget().refreshEntries();
        }

        this.initGui();
    }

    private void reanalyze()
    {
        this.converted.clear();
        this.analysis = null;
        this.batchError = null;
        this.analysisIndex = new int[this.rules.size()];

        Scope batchScope = BatchRules.batchScope(this.batchSavedScope());

        if (batchScope == null)
        {
            this.batchError = Message.of("lmswap.gui.scope.selection_unavailable." + this.selectionAvailability.name().toLowerCase(Locale.ROOT));
        }

        List<BatchRule> batchRules = new ArrayList<>();

        for (int i = 0; i < this.rules.size(); ++i)
        {
            BatchRules.Converted c = BatchRules.convert(this.rules.get(i));
            this.converted.add(c);
            this.analysisIndex[i] = c.rule() != null ? batchRules.size() : -1;

            if (c.rule() != null)
            {
                batchRules.add(c.rule());
            }
        }

        if (this.rules.size() > BatchReplacer.MAX_RULES)
        {
            this.batchError = Message.of("lmswap.error.batch_too_many", BatchReplacer.MAX_RULES);
        }

        if (this.batchError == null && batchRules.isEmpty() == false)
        {
            this.analysis = BatchReplacer.analyze(this.context.schematic(), this.context.placement(), batchRules,
                                                  batchScope, this.chainMode, MaterialResolver.LITEMATICA);
        }
    }

    @Nullable
    private Message ruleError(int index)
    {
        BatchRules.Converted c = this.converted.get(index);

        if (c.error() != null)
        {
            return c.error();
        }

        int a = this.analysisIndex[index];
        return this.analysis != null && a >= 0 ? this.analysis.ruleErrors().get(a) : null;
    }

    int hits(int index)
    {
        int a = this.analysisIndex[index];
        return this.analysis != null && a >= 0 ? this.analysis.hits()[a] : 0;
    }

    int shadowed(int index)
    {
        int a = this.analysisIndex[index];
        return this.analysis != null && a >= 0 ? this.analysis.shadowed()[a] : 0;
    }

    private boolean canExecute()
    {
        if (this.rules.isEmpty() || this.batchError != null || this.analysis == null || this.analysis.totalHits() == 0)
        {
            return false;
        }

        for (int i = 0; i < this.rules.size(); ++i)
        {
            if (this.ruleError(i) != null)
            {
                return false;
            }
        }

        return true;
    }

    // ---- actions ----

    void editRule(int index)
    {
        SavedRule rule = this.rules.get(index);
        Item source = rule.resolveSource().orElse(null);

        if (source == null)
        {
            this.addMessage(MessageType.ERROR, "lmswap.error.rule_unknown_id", rule.sourceItem());
            return;
        }

        MaterialListEntry entry = new MaterialListEntry(new ItemStack(source), 0, 0, 0, 0);
        Scope batchScope = BatchRules.batchScope(this.batchSavedScope());
        GuiMaterialReplace editor = new GuiMaterialReplace(this.context, entry, rule, edited -> this.replaceRule(index, edited),
                                                           batchScope != null ? batchScope : Scope.WHOLE);
        editor.setParent(this);
        GuiBase.openGui(editor);
    }

    private void openSourcePicker()
    {
        Scope batchScope = BatchRules.batchScope(this.batchSavedScope());
        GuiBatchSourcePicker picker = new GuiBatchSourcePicker(this.context, this::addRule, batchScope != null ? batchScope : Scope.WHOLE);
        picker.setParent(this);
        GuiBase.openGui(picker);
    }

    private void saveRuleSet()
    {
        GuiTextInput input = new GuiTextInput(64, "lmswap.gui.title.ruleset_name", "", this,
                                              (IStringConsumerFeedback) name -> {
                                                  String trimmed = name.trim();

                                                  if (trimmed.isEmpty())
                                                  {
                                                      return false;
                                                  }

                                                  RuleStore.INSTANCE.putRuleSet(trimmed, new RuleSet(this.batchSavedScope(), this.chainMode, this.rules));
                                                  this.addMessage(MessageType.SUCCESS, "lmswap.info.ruleset_saved", trimmed);
                                                  return true;
                                              });
        GuiBase.openGui(input);
    }

    private void loadRuleSet(RuleSet set)
    {
        this.rules.clear();
        this.rules.addAll(set.rules());
        this.chainMode = set.chainMode();
        this.scopeType = set.scope().type() == SavedScope.Type.INHERIT ? SavedScope.Type.ALL : set.scope().type();

        if (this.scopeType == SavedScope.Type.REGIONS)
        {
            this.selectedRegions.clear();
            this.selectedRegions.addAll(set.scope().regions());
            this.selectedRegions.retainAll(this.regionNames);
        }

        this.selectedIndex = this.rules.isEmpty() ? -1 : 0;
        this.changed();
    }

    private void execute()
    {
        if (this.canExecute() == false)
        {
            return;
        }

        if (this.context.isStillLoaded() == false)
        {
            this.addMessage(MessageType.ERROR, "lmswap.error.schematic_not_loaded");
            return;
        }

        if (this.analysis != null && this.analysis.chains().isEmpty() == false)
        {
            IConfirmationListener listener = new IConfirmationListener()
            {
                @Override
                public boolean onActionConfirmed()
                {
                    GuiBatchReplace.this.doExecute();
                    return true;
                }

                @Override
                public boolean onActionCancelled()
                {
                    return true;
                }
            };

            String modeKey = this.chainMode == ChainMode.ORIGINAL ? "lmswap.gui.confirm.chain_original" : "lmswap.gui.confirm.chain_sequential";
            GuiBase.openGui(new GuiConfirmAction(340, "lmswap.gui.title.confirm_chain", listener, this, modeKey,
                                                 this.chainText(this.analysis.chains().getFirst())));
            return;
        }

        this.doExecute();
    }

    private void doExecute()
    {
        List<BatchRule> batchRules = new ArrayList<>();
        this.converted.forEach(c -> batchRules.add(c.rule()));
        Scope batchScope = BatchRules.batchScope(this.batchSavedScope());

        if (batchScope == null || batchRules.contains(null))
        {
            return;
        }

        ReplacementResult result = MaterialSwapService.replaceBatch(this.context.schematic(), this.context.placement(), batchRules,
                                                                    batchScope, this.chainMode,
                                                                    StringUtils.translate("lmswap.gui.label.batch_undo_name"),
                                                                    StringUtils.translate("lmswap.gui.label.batch_undo_rules", batchRules.size()));

        if (result.isSuccess() && result.change() != null)
        {
            this.context.refreshMaterialList();
        }

        if (this.getParent() instanceof GuiBase gui)
        {
            if (result.isSuccess() == false)
            {
                gui.addMessage(MessageType.ERROR, result.error().translationKey(), result.error().args().toArray());
            }
            else
            {
                gui.addMessage(MessageType.SUCCESS, "lmswap.info.batch_done", result.totalReplaced(), batchRules.size());
                result.warnings().forEach(w -> gui.addMessage(MessageType.WARNING, w.translationKey(), w.args().toArray()));
            }
        }

        GuiBase.openGui(this.getParent());
    }

    // ---- GUI ----

    @Override
    protected int getBrowserWidth()
    {
        return this.getScreenWidth() - 20 - PANEL_WIDTH - 8;
    }

    @Override
    protected int getBrowserHeight()
    {
        return Math.max(60, this.getScreenHeight() - LIST_Y - BOTTOM_HEIGHT);
    }

    @Override
    protected RowList createListWidget(int listX, int listY)
    {
        return new RowList(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
    }

    @Override
    public void onSelectionChange(@Nullable BatchRow row)
    {
        this.selectedIndex = row != null ? row.index() : -1;
    }

    @Override
    public boolean onKeyTyped(KeyEvent input)
    {
        if (GuiBase.isShiftDown() && this.selectedIndex >= 0
            && (input.key() == ScanCodes.SCAN_UP || input.key() == ScanCodes.SCAN_DOWN))
        {
            this.moveRule(this.selectedIndex, input.key() == ScanCodes.SCAN_UP ? -1 : 1);
            return true;
        }

        return super.onKeyTyped(input);
    }

    @Override
    public void initGui()
    {
        super.initGui();

        if (this.getListWidget() != null && this.selectedIndex >= 0 && this.selectedIndex < this.rules.size())
        {
            this.getListWidget().setLastSelectedEntry(new BatchRow(this.selectedIndex, this.rules.get(this.selectedIndex)), this.selectedIndex);
        }

        int x = 10 + this.getBrowserWidth() + 8;
        int y = LIST_Y;
        int w = PANEL_WIDTH;

        // Batch area
        this.addLabel(x, y, w, 10, 0xFFFFFFFF, GuiBase.TXT_BOLD + StringUtils.translate("lmswap.gui.label.batch_scope"));
        y += 12;
        y = this.scopeButton(x, y, w, SavedScope.Type.ALL, "lmswap.gui.button.scope.whole");
        y = this.scopeButton(x, y, w, SavedScope.Type.REGIONS, "lmswap.gui.button.scope.regions");
        y = this.scopeButton(x, y, w, SavedScope.Type.SELECTION, "lmswap.gui.button.scope.selection");

        if (this.scopeType == SavedScope.Type.REGIONS)
        {
            int shown = 0;

            for (String region : this.regionNames)
            {
                if (shown++ == 4)
                {
                    this.addLabel(x + 4, y, w, 10, 0xFFA0A0A0, StringUtils.translate("lmswap.gui.label.rules_more", this.regionNames.size() - 4));
                    y += 12;
                    break;
                }

                WidgetCheckBox box = new WidgetCheckBox(x + 4, y, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED,
                                                        StringUtils.clampTextToRenderLength(region, w - 20, LeftRight.RIGHT, "..."));
                box.setChecked(this.selectedRegions.contains(region), false);
                box.setListener(cb -> {
                    if (cb.isChecked()) this.selectedRegions.add(region); else this.selectedRegions.remove(region);
                    this.changed();
                });
                this.addWidget(box);
                y += 12;
            }
        }

        // Chain mode
        y += 4;
        this.addLabel(x, y, w, 10, 0xFFFFFFFF, GuiBase.TXT_BOLD + StringUtils.translate("lmswap.gui.label.batch_chain_mode"));
        y += 12;
        y = this.chainButton(x, y, w, ChainMode.ORIGINAL, "lmswap.gui.button.chain_original");
        y = this.chainButton(x, y, w, ChainMode.SEQUENTIAL, "lmswap.gui.button.chain_sequential");

        // Actions
        y += 4;
        ButtonGeneric add = new ButtonGeneric(x, y, w, 16, StringUtils.translate("lmswap.gui.button.batch_add_rule"));
        this.addButton(add, (b, m) -> this.openSourcePicker());
        y += 17;
        ButtonGeneric save = new ButtonGeneric(x, y, w, 16, StringUtils.translate("lmswap.gui.button.batch_save_set"));
        save.setEnabled(this.rules.isEmpty() == false);
        this.addButton(save, (b, m) -> this.saveRuleSet());
        y += 21;

        int maxY = LIST_Y + this.getBrowserHeight();
        y = this.ruleSetList(x, y, w, maxY);
        this.templateList(x, y, w, maxY);

        // Bottom: preview summary and buttons
        int buttonY = this.getScreenHeight() - 26;
        ButtonGeneric cancel = new ButtonGeneric(this.getScreenWidth() - 10, buttonY, -1, true, "lmswap.gui.button.cancel");
        this.addButton(cancel, (b, m) -> this.closeGui(true));
        ButtonGeneric run = new ButtonGeneric(cancel.getX() - 4, buttonY, -1, true, "lmswap.gui.button.batch_execute");
        run.setEnabled(this.canExecute());
        this.addButton(run, (b, m) -> this.execute());
    }

    private int scopeButton(int x, int y, int w, SavedScope.Type type, String key)
    {
        boolean active = this.scopeType == type;
        ButtonGeneric button = new ButtonGeneric(x, y, w, 16, (active ? "» " : "") + StringUtils.translate(key));
        button.setEnabled(active == false);

        if (type == SavedScope.Type.SELECTION && this.selectionAvailability != SelectionScopes.Availability.AVAILABLE)
        {
            button.setEnabled(false);
            button.setHoverStrings("lmswap.gui.scope.selection_unavailable." + this.selectionAvailability.name().toLowerCase(Locale.ROOT));
        }

        this.addButton(button, (b, m) -> {
            this.scopeType = type;
            this.selectionAvailability = SelectionScopes.check(this.context.placement());
            this.changed();
        });
        return y + 17;
    }

    private int chainButton(int x, int y, int w, ChainMode mode, String key)
    {
        boolean active = this.chainMode == mode;
        ButtonGeneric button = new ButtonGeneric(x, y, w, 16, (active ? "» " : "") + StringUtils.translate(key),
                                                 StringUtils.translate(key + ".hover"));
        button.setEnabled(active == false);
        this.addButton(button, (b, m) -> {
            this.chainMode = mode;
            this.changed();
        });
        return y + 17;
    }

    private int ruleSetList(int x, int y, int w, int maxY)
    {
        Map<String, RuleSet> sets = RuleStore.INSTANCE.getRuleSets();

        if (sets.isEmpty() || y > maxY - 24)
        {
            return y;
        }

        this.addLabel(x, y, w, 10, 0xFFFFFFFF, GuiBase.TXT_BOLD + StringUtils.translate("lmswap.gui.label.batch_rule_sets"));
        y += 12;

        List<String> names = new ArrayList<>(sets.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);

        for (String name : names)
        {
            if (y > maxY - 14)
            {
                break;
            }

            ButtonGeneric delete = new ButtonGeneric(x, y, 14, 12, "x", StringUtils.translate("lmswap.gui.button.ruleset_delete.hover"));
            this.addButton(delete, (b, m) -> {
                RuleStore.INSTANCE.removeRuleSet(name);
                this.initGui();
            });
            String label = StringUtils.clampTextToRenderLength(name + GuiBase.TXT_GRAY + " (" + sets.get(name).rules().size() + ")",
                                                               w - 18, LeftRight.RIGHT, "...");
            ButtonGeneric load = new ButtonGeneric(x + 16, y, w - 16, 12, label, StringUtils.translate("lmswap.gui.button.ruleset_load.hover"));
            this.addButton(load, (b, m) -> this.loadRuleSet(sets.get(name)));
            y += 13;
        }

        return y + 4;
    }

    private void templateList(int x, int y, int w, int maxY)
    {
        List<SavedRule> templates = new ArrayList<>(RuleStore.INSTANCE.getSchematicRules(this.context.schematic()));
        List<SavedRule> global = RuleStore.INSTANCE.getGlobalRules();
        templates.addAll(global);

        if (templates.isEmpty() || y > maxY - 24)
        {
            return;
        }

        this.addLabel(x, y, w, 10, 0xFFFFFFFF, GuiBase.TXT_BOLD + StringUtils.translate("lmswap.gui.label.batch_templates"));
        y += 12;

        for (int i = 0; i < templates.size(); ++i)
        {
            if (y > maxY - 12)
            {
                this.addLabel(x, y, w, 10, 0xFFA0A0A0, StringUtils.translate("lmswap.gui.label.rules_more", templates.size() - i));
                break;
            }

            SavedRule template = templates.get(i);
            boolean isGlobal = i >= templates.size() - global.size();
            String label = "+ " + StringUtils.clampTextToRenderLength(RuleTexts.describe(template, isGlobal), w - 16, LeftRight.RIGHT, "...");
            ButtonGeneric button = new ButtonGeneric(x, y, w, 12, label, StringUtils.translate("lmswap.gui.button.template_add.hover"));
            this.addButton(button, (b, m) -> this.addRule(template));
            y += 13;
        }
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        int x = 12;
        int y = LIST_Y + this.getBrowserHeight() + 4;
        int maxWidth = this.getScreenWidth() - 24;

        if (this.rules.isEmpty())
        {
            this.drawString(ctx, GuiBase.TXT_GRAY + StringUtils.translate("lmswap.gui.label.batch_empty"), x, y, 0xFFFFFFFF);
            return;
        }

        if (this.batchError != null)
        {
            this.drawString(ctx, GuiBase.TXT_RED + StringUtils.translate(this.batchError.translationKey(), this.batchError.args().toArray()), x, y, 0xFFFFFFFF);
            return;
        }

        int total = this.analysis != null ? this.analysis.totalHits() : 0;
        String totalText = StringUtils.translate("lmswap.gui.label.batch_total", total, this.rules.size(),
                                                 StringUtils.translate(this.chainMode == ChainMode.ORIGINAL ? "lmswap.gui.button.chain_original"
                                                                                                             : "lmswap.gui.button.chain_sequential"));
        this.drawString(ctx, totalText, x, y, 0xFFFFFFFF);
        y += 11;

        if (this.analysis != null && this.analysis.materialDelta().isEmpty() == false)
        {
            String delta = StringUtils.translate("lmswap.gui.label.batch_delta", this.deltaText(this.analysis.materialDelta()));
            this.drawString(ctx, StringUtils.clampTextToRenderLength(delta, maxWidth, LeftRight.RIGHT, "..."), x, y, 0xFFFFFFFF);
            y += 11;
        }

        List<String> warnings = this.warningLines();

        for (int i = 0; i < warnings.size() && i < MAX_WARNING_LINES; ++i)
        {
            this.drawString(ctx, StringUtils.clampTextToRenderLength(warnings.get(i), maxWidth, LeftRight.RIGHT, "..."), x, y, 0xFFFFFFFF);
            y += 11;
        }

        if (warnings.size() > MAX_WARNING_LINES)
        {
            this.drawString(ctx, GuiBase.TXT_GOLD + StringUtils.translate("lmswap.gui.label.rules_more", warnings.size() - MAX_WARNING_LINES), x, y, 0xFFFFFFFF);
        }
    }

    private String deltaText(Map<Item, Integer> delta)
    {
        List<Map.Entry<Item, Integer>> entries = new ArrayList<>(delta.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<Item, Integer> e) -> Math.abs(e.getValue())).reversed());
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Item, Integer> e : entries)
        {
            if (sb.length() > 0)
            {
                sb.append(GuiBase.TXT_GRAY).append(", ").append(GuiBase.TXT_RST);
            }

            int v = e.getValue();
            sb.append(new ItemStack(e.getKey()).getHoverName().getString()).append(' ')
              .append(v > 0 ? GuiBase.TXT_GREEN + "+" + v : GuiBase.TXT_RED + v).append(GuiBase.TXT_RST);
        }

        return sb.toString();
    }

    private List<String> warningLines()
    {
        List<String> lines = new ArrayList<>();

        for (int i = 0; i < this.rules.size(); ++i)
        {
            Message error = this.ruleError(i);

            if (error != null)
            {
                lines.add(GuiBase.TXT_RED + "#" + (i + 1) + ": " + StringUtils.translate(error.translationKey(), error.args().toArray()));
            }
        }

        if (this.analysis != null)
        {
            for (int[] chain : this.analysis.chains())
            {
                lines.add(GuiBase.TXT_GOLD + StringUtils.translate("lmswap.warning.batch_chain", this.chainText(chain)));
            }
        }

        for (int i = 0; i < this.rules.size(); ++i)
        {
            if (this.shadowed(i) > 0)
            {
                lines.add(GuiBase.TXT_GOLD + StringUtils.translate("lmswap.warning.batch_overlap", i + 1, this.shadowed(i)));
            }
        }

        if (this.context.placements().size() > 1)
        {
            lines.add(GuiBase.TXT_GOLD + StringUtils.translate("lmswap.warning.shared_schematic", this.context.placements().size()));
        }

        return lines;
    }

    /** "#1 Smooth Stone Slab → Stone Slab → #2 Andesite Slab" for a chain (analysis indices) */
    private String chainText(int[] chain)
    {
        int first = this.ruleIndexFor(chain[0]);
        int second = this.ruleIndexFor(chain[1]);
        SavedRule a = this.rules.get(first);
        SavedRule b = this.rules.get(second);
        return "#" + (first + 1) + " " + a.sourceName() + " → " + a.targetName() + " → #" + (second + 1) + " " + b.targetName();
    }

    private int ruleIndexFor(int analysisIdx)
    {
        for (int i = 0; i < this.analysisIndex.length; ++i)
        {
            if (this.analysisIndex[i] == analysisIdx)
            {
                return i;
            }
        }

        return 0;
    }

    // ---- list ----

    public static class RowList extends WidgetListBase<BatchRow, RowWidget>
    {
        private final GuiBatchReplace gui;

        public RowList(int x, int y, int width, int height, GuiBatchReplace gui)
        {
            super(x, y, width, height, gui);

            this.gui = gui;
            this.browserEntryHeight = 18;
        }

        @Override
        protected Collection<BatchRow> getAllEntries()
        {
            List<BatchRow> rows = new ArrayList<>();

            for (int i = 0; i < this.gui.rules.size(); ++i)
            {
                rows.add(new BatchRow(i, this.gui.rules.get(i)));
            }

            return rows;
        }

        @Override
        protected RowWidget createListEntryWidget(int x, int y, int listIndex, boolean isOdd, BatchRow row)
        {
            return new RowWidget(x, y, this.browserEntryWidth, this.getBrowserEntryHeightFor(row), isOdd, row, listIndex, this.gui);
        }
    }

    public static class RowWidget extends WidgetListEntryBase<BatchRow>
    {
        private final boolean isOdd;
        private final String text;
        private final String stats;
        private final int buttonsX;

        public RowWidget(int x, int y, int width, int height, boolean isOdd, BatchRow row, int listIndex, GuiBatchReplace gui)
        {
            super(x, y, width, height, row, listIndex);

            this.isOdd = isOdd;
            int index = row.index();
            int bx = x + width - 4;

            bx = this.rowButton(bx, y + 2, "x", "lmswap.gui.button.batch_remove.hover", () -> gui.removeRule(index));
            bx = this.rowButton(bx, y + 2, "✎", "lmswap.gui.button.batch_edit.hover", () -> gui.editRule(index));
            bx = this.rowButton(bx, y + 2, "↓", "lmswap.gui.button.batch_down.hover", () -> gui.moveRule(index, 1));
            bx = this.rowButton(bx, y + 2, "↑", "lmswap.gui.button.batch_up.hover", () -> gui.moveRule(index, -1));
            this.buttonsX = bx;

            Message error = gui.ruleError(index);
            int hits = gui.hits(index);
            int shadowed = gui.shadowed(index);
            this.stats = error != null ? GuiBase.TXT_RED + "!" :
                         (hits > 0 ? GuiBase.TXT_GREEN : GuiBase.TXT_GRAY) + hits + (shadowed > 0 ? GuiBase.TXT_GOLD + " (" + shadowed + ")" : "");
            this.text = "#" + (index + 1) + " " + RuleTexts.describe(row.rule(), false);
        }

        private int rowButton(int xRight, int y, String label, String hoverKey, Runnable action)
        {
            int w = 14;
            ButtonGeneric button = new ButtonGeneric(xRight - w, y, w, 14, label, StringUtils.translate(hoverKey));
            this.addButton(button, (b, m) -> action.run());
            return xRight - w - 2;
        }

        @Override
        public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected)
        {
            int color = selected ? 0xA0707070 : this.isMouseOver(mouseX, mouseY) ? 0xA0505050 : (this.isOdd ? 0xA0101010 : 0xA0303030);
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, color);

            int statsWidth = this.getStringWidth(this.stats);
            int textMax = this.buttonsX - this.x - statsWidth - 16;
            this.drawString(ctx, this.x + 4, this.y + 5, 0xFFFFFFFF, StringUtils.clampTextToRenderLength(this.text, textMax, LeftRight.RIGHT, "..."));
            this.drawString(ctx, this.buttonsX - statsWidth - 6, this.y + 5, 0xFFFFFFFF, this.stats);

            super.render(ctx, mouseX, mouseY, selected);
        }
    }
}
