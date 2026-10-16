package de.nils.lmswap;

import java.util.Arrays;

import de.nils.lmswap.mixin.MaterialListPlacementAccessor;
import de.nils.lmswap.mixin.MaterialListSchematicAccessor;
import de.nils.lmswap.mixin.SchematicPlacementAccessor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads the Litematica target classes (without initializing them) so Mixin applies our mixins. A changed target
 * (renamed field, constructor signature) fails here at build time instead of in game.
 */
class MixinApplicationTest
{
    private static Class<?> load(String name) throws ClassNotFoundException
    {
        return Class.forName(name, false, MixinApplicationTest.class.getClassLoader());
    }

    @Test
    void accessorsAreApplied() throws ClassNotFoundException
    {
        assertTrue(SchematicPlacementAccessor.class.isAssignableFrom(
                load("fi.dy.masa.litematica.schematic.placement.SchematicPlacement")));
        assertTrue(MaterialListPlacementAccessor.class.isAssignableFrom(
                load("fi.dy.masa.litematica.materials.MaterialListPlacement")));
        assertTrue(MaterialListSchematicAccessor.class.isAssignableFrom(
                load("fi.dy.masa.litematica.materials.MaterialListSchematic")));
    }

    @Test
    void replaceButtonInjectionIsApplied() throws ClassNotFoundException
    {
        Class<?> clazz = load("fi.dy.masa.litematica.gui.widgets.WidgetMaterialListEntry");

        assertTrue(Arrays.stream(clazz.getDeclaredMethods()).anyMatch(m -> m.getName().contains("lmswap$addReplaceButton")),
                   "Injected handler not found in WidgetMaterialListEntry");
    }

    @Test
    void materialListButtonsAndReloadHookAreApplied() throws ClassNotFoundException
    {
        assertTrue(hasHandler("fi.dy.masa.litematica.gui.GuiMaterialList", "lmswap$addButtons"));
        assertTrue(hasHandler("fi.dy.masa.litematica.schematic.LitematicaSchematic", "lmswap$clearHistoryOnReload"));
    }

    @Test
    void autoApplyLoadHooksAreApplied() throws ClassNotFoundException
    {
        assertTrue(hasHandler("fi.dy.masa.litematica.schematic.LitematicaSchematic", "lmswap$queueAutoApplyOnReload"));
        assertTrue(hasHandler("fi.dy.masa.litematica.schematic.LitematicaSchematic", "lmswap$queueAutoApplyOnLoad"));
    }

    private static boolean hasHandler(String className, String handler) throws ClassNotFoundException
    {
        return Arrays.stream(load(className).getDeclaredMethods()).anyMatch(m -> m.getName().contains(handler));
    }
}
