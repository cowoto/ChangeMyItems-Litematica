package de.nils.lmswap.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.gui.widgets.WidgetListMaterialList;
import fi.dy.masa.litematica.gui.widgets.WidgetMaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntrySortable;

import de.nils.lmswap.gui.ReplaceButtons;

/**
 * Adds the "Replace" button to each material list row. Litematica creates its only row button ("Ignore")
 * right-aligned at the end of the constructor, so we hook in after it and place ours to its left.
 */
@Mixin(WidgetMaterialListEntry.class)
public abstract class WidgetMaterialListEntryMixin extends WidgetListEntrySortable<MaterialListEntry>
{
    private WidgetMaterialListEntryMixin(int x, int y, int width, int height, @Nullable MaterialListEntry entry, int listIndex)
    {
        super(x, y, width, height, entry, listIndex);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void lmswap$addReplaceButton(int x, int y, int width, int height, boolean isOdd,
                                         MaterialListBase materialList, @Nullable MaterialListEntry entry,
                                         int listIndex, WidgetListMaterialList listWidget, CallbackInfo ci)
    {
        if (entry == null)
        {
            return;
        }

        int leftmostButtonX = x + width;

        for (WidgetBase widget : this.subWidgets)
        {
            leftmostButtonX = Math.min(leftmostButtonX, widget.getX());
        }

        ReplaceButtons.create(materialList, entry, leftmostButtonX - 1, y + 1)
                      .ifPresent(button -> this.addButton(button.button(), button.listener()));
    }
}
