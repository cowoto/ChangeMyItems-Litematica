package de.nils.lmswap.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import de.nils.lmswap.core.MaterialResolver;
import de.nils.lmswap.core.Scope;
import de.nils.lmswap.core.SchematicReplacer;
import de.nils.lmswap.data.SavedRule;

/**
 * Batch: choose the source material of a new rule from the materials of the schematic (same grouping as the material
 * list), then edit the rule in the replace screen (edit mode).
 */
public class GuiBatchSourcePicker extends GuiListBase<GuiBatchSourcePicker.Material, GuiBatchSourcePicker.Row, GuiBatchSourcePicker.MaterialList>
                                  implements ISelectionListener<GuiBatchSourcePicker.Material>
{
    private final ReplaceContext context;
    private final Consumer<SavedRule> ruleConsumer;
    private final Scope batchScope;
    private final List<Material> materials;

    public record Material(Item item, int count)
    {
    }

    public GuiBatchSourcePicker(ReplaceContext context, Consumer<SavedRule> ruleConsumer, Scope batchScope)
    {
        super(10, 30);

        this.context = context;
        this.ruleConsumer = ruleConsumer;
        this.batchScope = batchScope;
        this.title = StringUtils.translate("lmswap.gui.title.batch_source");
        this.useTitleHierarchy = false;
        this.materials = collect(context);
    }

    private static List<Material> collect(ReplaceContext context)
    {
        Map<Item, Integer> counts = new LinkedHashMap<>();

        for (Map.Entry<BlockState, Integer> e : SchematicReplacer.countAllStates(context.schematic()).entrySet())
        {
            Item item = MaterialResolver.LITEMATICA.sourceItem(e.getKey());

            if (item != null)
            {
                counts.merge(item, e.getValue(), Integer::sum);
            }
        }

        List<Material> list = new ArrayList<>();
        counts.forEach((item, count) -> list.add(new Material(item, count)));
        list.sort(Comparator.comparing(m -> new ItemStack(m.item()).getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    @Override
    protected int getBrowserWidth()
    {
        return this.getScreenWidth() - 20;
    }

    @Override
    protected int getBrowserHeight()
    {
        return this.getScreenHeight() - 60;
    }

    @Override
    protected MaterialList createListWidget(int listX, int listY)
    {
        return new MaterialList(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this.materials, this);
    }

    @Override
    public void initGui()
    {
        super.initGui();

        ButtonGeneric cancel = new ButtonGeneric(this.getScreenWidth() - 10, this.getScreenHeight() - 26, -1, true, "lmswap.gui.button.cancel");
        this.addButton(cancel, (b, m) -> this.closeGui(true));
    }

    @Override
    public void onSelectionChange(@Nullable Material material)
    {
        if (material == null)
        {
            return;
        }

        MaterialListEntry entry = new MaterialListEntry(new ItemStack(material.item()), material.count(), 0, 0, 0);
        GuiMaterialReplace editor = new GuiMaterialReplace(this.context, entry, null, this.ruleConsumer, this.batchScope);
        editor.setParent(this.getParent()); // after "Accept" go straight back to the batch screen
        GuiBase.openGui(editor);
    }

    public static class MaterialList extends WidgetListBase<Material, Row>
    {
        private final List<Material> materials;

        public MaterialList(int x, int y, int width, int height, List<Material> materials, ISelectionListener<Material> listener)
        {
            super(x, y, width, height, listener);

            this.materials = materials;
            this.browserEntryHeight = 22;
            this.browserEntriesOffsetY = 18;
            this.widgetSearchBar = new WidgetSearchBar(x + 2, y + 4, width - 14, 14, 0, Icons.FILE_ICON_SEARCH, LeftRight.LEFT);
            this.widgetSearchBar.setSearchOpen(true);
        }

        @Override
        protected Collection<Material> getAllEntries()
        {
            return this.materials;
        }

        @Override
        protected List<String> getEntryStringsForFilter(Material material)
        {
            return List.of(new ItemStack(material.item()).getHoverName().getString().toLowerCase(Locale.ROOT),
                           BuiltInRegistries.ITEM.getKey(material.item()).toString());
        }

        @Override
        protected Row createListEntryWidget(int x, int y, int listIndex, boolean isOdd, Material material)
        {
            return new Row(x, y, this.browserEntryWidth, this.getBrowserEntryHeightFor(material), isOdd, material, listIndex);
        }
    }

    public static class Row extends WidgetListEntryBase<Material>
    {
        private final boolean isOdd;
        private final ItemStack stack;
        private final String text;

        public Row(int x, int y, int width, int height, boolean isOdd, Material material, int listIndex)
        {
            super(x, y, width, height, material, listIndex);

            this.isOdd = isOdd;
            this.stack = new ItemStack(material.item());
            this.text = this.stack.getHoverName().getString() + GuiBase.TXT_GRAY + "  " + material.count();
        }

        @Override
        public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected)
        {
            int color = this.isMouseOver(mouseX, mouseY) ? 0xA0505050 : (this.isOdd ? 0xA0101010 : 0xA0303030);
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, color);
            RenderUtils.drawRect(ctx, this.x + 4, this.y + 3, 16, 16, 0x20FFFFFF);
            ctx.renderItem(this.stack, this.x + 4, this.y + 3);
            this.drawString(ctx, this.x + 26, this.y + 7, 0xFFFFFFFF, this.text);
        }
    }
}
