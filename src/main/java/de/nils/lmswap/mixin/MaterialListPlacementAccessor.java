package de.nils.lmswap.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

@Mixin(MaterialListPlacement.class)
public interface MaterialListPlacementAccessor
{
    @Accessor("placement")
    SchematicPlacement lmswap$getPlacement();
}
