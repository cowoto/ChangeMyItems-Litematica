package de.nils.lmswap.mixin;

import java.nio.file.Path;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.util.FileType;

import de.nils.lmswap.core.AutoApplyHandler;
import de.nils.lmswap.data.ReplacementHistory;

@Mixin(LitematicaSchematic.class)
public abstract class LitematicaSchematicMixin
{
    /**
     * "Reload" in the loaded schematics list re-reads the file into the same instance. The recorded undo positions
     * would no longer match, so the history of that schematic is dropped.
     */
    @Inject(method = "readFromFile()Z", at = @At("HEAD"))
    private void lmswap$clearHistoryOnReload(CallbackInfoReturnable<Boolean> cir)
    {
        ReplacementHistory.INSTANCE.clear((LitematicaSchematic) (Object) this);
    }

    /** A reloaded schematic gets the saved rules again (if enabled) */
    @Inject(method = "readFromFile()Z", at = @At("RETURN"))
    private void lmswap$queueAutoApplyOnReload(CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ())
        {
            AutoApplyHandler.INSTANCE.onSchematicLoaded((LitematicaSchematic) (Object) this);
        }
    }

    /** Covers "Load Schematics" and placements restored from the placement file (SchematicHolder.getOrLoad) */
    @Inject(method = "createFromFile(Ljava/nio/file/Path;Ljava/lang/String;Lfi/dy/masa/litematica/util/FileType;)Lfi/dy/masa/litematica/schematic/LitematicaSchematic;",
            at = @At("RETURN"))
    private static void lmswap$queueAutoApplyOnLoad(Path dir, String fileName, FileType type,
                                                    CallbackInfoReturnable<LitematicaSchematic> cir)
    {
        if (cir.getReturnValue() != null)
        {
            AutoApplyHandler.INSTANCE.onSchematicLoaded(cir.getReturnValue());
        }
    }
}
