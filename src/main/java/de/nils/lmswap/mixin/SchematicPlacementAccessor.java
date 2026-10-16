package de.nils.lmswap.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

/**
 * {@link SchematicPlacement#getMaterialList()} creates the list on first access, which already schedules a count.
 * This accessor lets us refresh only lists that exist.
 */
@Mixin(SchematicPlacement.class)
public interface SchematicPlacementAccessor
{
    @Accessor("materialList")
    @Nullable
    MaterialListBase lmswap$getMaterialListOrNull();
}
