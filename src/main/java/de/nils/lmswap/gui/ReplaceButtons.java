package de.nils.lmswap.gui;

import java.util.Optional;

import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.GuiUtils;
import net.minecraft.world.item.BlockItem;

/**
 * Creates the "Replace" button for a material list row. Kept out of the mixin so the mixin stays minimal.
 */
public final class ReplaceButtons
{
    private ReplaceButtons()
    {
    }

    public record RowButton(ButtonGeneric button, IButtonActionListener listener)
    {
    }

    /**
     * @param xRight right edge of the button
     * @return empty if the entry can not be replaced (no block item, or a list without a loaded schematic)
     */
    public static Optional<RowButton> create(MaterialListBase materialList, MaterialListEntry entry, int xRight, int y)
    {
        if ((entry.getStack().getItem() instanceof BlockItem) == false)
        {
            return Optional.empty();
        }

        return ReplaceContext.of(materialList).map(context -> {
            ButtonGeneric button = new ButtonGeneric(xRight, y, -1, true, "lmswap.gui.button.material_list.replace");
            button.setHoverStrings("lmswap.gui.button.material_list.replace.hover");

            IButtonActionListener listener = (btn, mouseButton) -> {
                GuiMaterialReplace gui = new GuiMaterialReplace(context, entry);
                gui.setParent(GuiUtils.getCurrentScreen());
                GuiBase.openGui(gui);
            };

            return new RowButton(button, listener);
        });
    }
}
