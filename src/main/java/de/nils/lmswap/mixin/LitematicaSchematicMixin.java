package de.nils.lmswap.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import de.nils.lmswap.data.ReplacementHistory;

/**
 * "Reload" in the loaded schematics list re-reads the file into the same instance. The recorded undo positions
 * would no longer match, so the history of that schematic is dropped.
 */
@Mixin(LitematicaSchematic.class)
public abstract class LitematicaSchematicMixin
{
    @Inject(method = "readFromFile()Z", at = @At("HEAD"))
    private void lmswap$clearHistoryOnReload(CallbackInfoReturnable<Boolean> cir)
    {
        ReplacementHistory.INSTANCE.clear((LitematicaSchematic) (Object) this);
    }
}
