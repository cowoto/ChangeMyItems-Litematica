package de.nils.lmswap.gui;

import java.util.Optional;

import fi.dy.masa.litematica.gui.GuiMainMenu.ButtonListenerChangeMenu;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

import de.nils.lmswap.core.MaterialSwapService;
import de.nils.lmswap.data.ReplacementHistory;

/**
 * The "Undo last replacement" and "Save as new schematic" buttons in Litematica's material list, placed in the bottom
 * row to the left of Litematica's "Main menu" button.
 */
public final class MaterialListButtons
{
    private MaterialListButtons()
    {
    }

    public static void addTo(GuiMaterialList gui)
    {
        Optional<ReplaceContext> optional = ReplaceContext.of(gui.getMaterialList());

        if (optional.isEmpty())
        {
            return;
        }

        ReplaceContext context = optional.get();
        int y = gui.getScreenHeight() - 36;

        // Same position calculation as GuiMaterialList.createButtons() uses for the main menu button
        String mainMenuLabel = StringUtils.translate(ButtonListenerChangeMenu.ButtonType.MAIN_MENU.getLabelKey());
        int mainMenuX = gui.getScreenWidth() - (gui.getStringWidth(mainMenuLabel) + 20) - 10;

        ButtonGeneric save = new ButtonGeneric(mainMenuX - 2, y, -1, true, "lmswap.gui.button.material_list.save_as");
        save.setHoverStrings("lmswap.gui.button.material_list.save_as.hover", "lmswap.gui.button.material_list.save_as.hover2");
        gui.addButton(save, (button, mouseButton) -> SaveAsHandler.open(context, gui));

        ButtonGeneric undo = new ButtonGeneric(save.getX() - 2, y, -1, true, "lmswap.gui.button.material_list.undo");
        Optional<ReplacementHistory.Entry> last = ReplacementHistory.INSTANCE.peek(context.schematic());

        if (last.isPresent())
        {
            ReplacementHistory.Entry entry = last.get();
            undo.setHoverStrings(StringUtils.translate("lmswap.gui.button.material_list.undo.hover", entry.sourceName(),
                                                       entry.targetName(), entry.count()),
                                 StringUtils.translate("lmswap.gui.button.material_list.undo.hover2",
                                                       ReplacementHistory.INSTANCE.size(context.schematic())));
        }
        else
        {
            undo.setEnabled(false);
            undo.setHoverStrings("lmswap.gui.button.material_list.undo.empty");
        }

        gui.addButton(undo, (button, mouseButton) -> undo(context, gui));
    }

    private static void undo(ReplaceContext context, GuiMaterialList gui)
    {
        Optional<ReplacementHistory.Entry> undone = MaterialSwapService.undoLast(context.schematic());

        if (undone.isPresent())
        {
            ReplacementHistory.Entry entry = undone.get();
            context.refreshMaterialList();
            gui.addMessage(MessageType.SUCCESS, "lmswap.info.undone", entry.count(), entry.sourceName(), entry.targetName());
        }

        // Re-create the buttons so the undo button reflects the remaining history
        gui.initGui();
    }
}
