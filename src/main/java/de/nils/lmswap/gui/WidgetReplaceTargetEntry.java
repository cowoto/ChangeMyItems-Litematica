package de.nils.lmswap.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * One target block: item icon, display name and id. Air is shown as "remove material".
 */
public class WidgetReplaceTargetEntry extends WidgetListEntryBase<Block>
{
    private final boolean isOdd;
    private final ItemStack icon;
    private final String name;
    private final String id;

    public WidgetReplaceTargetEntry(int x, int y, int width, int height, boolean isOdd, Block block, int listIndex)
    {
        super(x, y, width, height, block, listIndex);

        this.isOdd = isOdd;
        this.icon = new ItemStack(block.asItem());
        this.name = displayName(block);
        this.id = BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    static String displayName(Block block)
    {
        return block.defaultBlockState().isAir()
               ? StringUtils.translate("lmswap.gui.label.target_air")
               : block.getName().getString();
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected)
    {
        if (selected)
        {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0707070);
            RenderUtils.drawOutline(ctx, this.x, this.y, this.width, this.height, 0xFFE0E0E0);
        }
        else if (this.isMouseOver(mouseX, mouseY))
        {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0505050);
        }
        else
        {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, this.isOdd ? 0xA0101010 : 0xA0303030);
        }

        int iconX = this.x + 4;
        int iconY = this.y + 3;
        RenderUtils.drawRect(ctx, iconX, iconY, 16, 16, 0x20FFFFFF);

        if (this.icon.isEmpty() == false)
        {
            ctx.renderItem(this.icon, iconX, iconY);
        }

        int textY = this.y + 7;
        this.drawString(ctx, iconX + 22, textY, 0xFFFFFFFF, this.name);
        this.drawString(ctx, iconX + 30 + this.getStringWidth(this.name), textY, 0xFFFFFFFF, GuiBase.TXT_GRAY + this.id);
    }
}
