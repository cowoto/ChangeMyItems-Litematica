package de.nils.lmswap.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.gui.GuiMaterialList;

import de.nils.lmswap.gui.MaterialListButtons;

/**
 * Adds "Undo last replacement" and "Save as new schematic" to the material list. initGui() is public and recreates
 * all buttons each time, so hooking its end is enough.
 */
@Mixin(GuiMaterialList.class)
public abstract class GuiMaterialListMixin
{
    @Inject(method = "initGui", at = @At("TAIL"))
    private void lmswap$addButtons(CallbackInfo ci)
    {
        MaterialListButtons.addTo((GuiMaterialList) (Object) this);
    }
}
