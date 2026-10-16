package de.nils.lmswap.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
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

import de.nils.lmswap.core.ItemReplacementPlan;
import de.nils.lmswap.core.MaterialSwapService;
import de.nils.lmswap.core.ReplacementResult;

/**
 * "Replace material" screen, opened from a material list row.
 * Source on top, searchable target list, options, preview and the replace/cancel buttons at the bottom.
 */
public class GuiMaterialReplace extends GuiListBase<Block, WidgetReplaceTargetEntry, WidgetListReplaceTargets>
                                implements ISelectionListener<Block>
{
    private static final int LIST_Y = 50;
    private static final int BOTTOM_AREA_HEIGHT = 96;

    private final ReplaceContext context;
    private final MaterialListEntry entry;
    private final ItemReplacementPlan plan;
    private final List<Block> targets = new ArrayList<>();
    private final int placementCount;
    private boolean keepProperties = true;
    @Nullable private Block selected;

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

        this.initGui();
    }

    @Override
    protected int getBrowserWidth()
    {
        return this.getScreenWidth() - 20;
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

        int buttonY = this.getScreenHeight() - 26;
        ButtonGeneric cancel = new ButtonGeneric(this.getScreenWidth() - 10, buttonY, -1, true, "lmswap.gui.button.cancel");
        this.addButton(cancel, (button, mouseButton) -> this.closeGui(true));

        ButtonGeneric replace = new ButtonGeneric(cancel.getX() - 4, buttonY, -1, true, "lmswap.gui.button.replace_confirm");
        replace.setEnabled(this.selected != null && this.plan.getBlockCount() > 0);
        this.addButton(replace, (button, mouseButton) -> this.replace());
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
        int y = LIST_Y + this.getBrowserHeight() + 20;

        if (this.plan.getBlockCount() == 0)
        {
            this.drawString(ctx, GuiBase.TXT_RED + StringUtils.translate("lmswap.error.nothing_to_replace"), x, y, 0xFFFFFFFF);
            return;
        }

        if (this.selected == null)
        {
            this.drawString(ctx, GuiBase.TXT_GRAY + StringUtils.translate("lmswap.gui.label.select_target"), x, y, 0xFFFFFFFF);
            return;
        }

        String preview = StringUtils.translate("lmswap.gui.label.preview", this.plan.getBlockCount(),
                                               this.entry.getStack().getHoverName().getString(),
                                               WidgetReplaceTargetEntry.displayName(this.selected));
        this.drawString(ctx, preview, x, y, 0xFFFFFFFF);

        for (ReplacementResult.Message warning : this.plan.previewWarnings(this.selected, this.keepProperties))
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

        ReplacementResult result = MaterialSwapService.replaceInSchematic(this.context.schematic(),
                                                                          this.plan.rulesFor(this.selected, this.keepProperties));
        Screen parent = this.getParent();

        if (result.isSuccess() && result.change() != null)
        {
            this.context.refreshMaterialList();
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
