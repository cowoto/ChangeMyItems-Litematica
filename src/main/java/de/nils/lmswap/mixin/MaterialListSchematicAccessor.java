package de.nils.lmswap.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

@Mixin(MaterialListSchematic.class)
public interface MaterialListSchematicAccessor
{
    @Accessor("schematic")
    LitematicaSchematic lmswap$getSchematic();
}
