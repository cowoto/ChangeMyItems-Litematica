package de.nils.lmswap.gui;

import java.nio.file.Files;
import java.nio.file.Path;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.util.FileNameUtils;

/**
 * "Save as new schematic": writes the (modified) loaded schematic to a new .litematic next to the original.
 * <p>
 * Existing files, and above all the original file, are only overwritten after an explicit confirmation. The loaded
 * schematic keeps pointing to its original file (Litematica's writeToFile does not change it), so "Reload" still
 * restores the original.
 */
public final class SaveAsHandler
{
    private static final String EXTENSION = ".litematic";

    private SaveAsHandler()
    {
    }

    public static void open(ReplaceContext context, GuiMaterialList parent)
    {
        if (context.isStillLoaded() == false)
        {
            parent.addMessage(MessageType.ERROR, "lmswap.error.schematic_not_loaded");
            return;
        }

        LitematicaSchematic schematic = context.schematic();
        Path dir = directoryFor(schematic);

        GuiTextInput gui = new GuiTextInput(256, "lmswap.gui.title.save_as", suggestName(schematic, dir), parent,
                                            (IStringConsumerFeedback) name -> save(schematic, dir, name, parent));
        GuiBase.openGui(gui);
    }

    /**
     * @return true to close the name dialog, false to keep it open (invalid name or a confirmation is shown)
     */
    private static boolean save(LitematicaSchematic schematic, Path dir, String nameIn, GuiMaterialList parent)
    {
        String name = stripExtension(nameIn.trim());

        if (name.isEmpty())
        {
            parent.addMessage(MessageType.ERROR, "lmswap.error.save_name_empty");
            return false;
        }

        Path target = targetPath(dir, name);

        if (Files.exists(target) == false)
        {
            write(schematic, dir, name, false, target, parent);
            return true;
        }

        boolean isOriginal = schematic.getFile() != null && target.equals(schematic.getFile().toAbsolutePath().normalize());
        String messageKey = isOriginal ? "lmswap.gui.confirm.overwrite_original" : "lmswap.gui.confirm.overwrite_existing";

        IConfirmationListener listener = new IConfirmationListener()
        {
            @Override
            public boolean onActionConfirmed()
            {
                write(schematic, dir, name, true, target, parent);
                return true;
            }

            @Override
            public boolean onActionCancelled()
            {
                return true;
            }
        };

        GuiBase.openGui(new GuiConfirmAction(320, "lmswap.gui.title.confirm_overwrite", listener, parent,
                                             messageKey, target.getFileName().toString()));
        return false;
    }

    private static void write(LitematicaSchematic schematic, Path dir, String name, boolean override, Path target,
                              GuiMaterialList parent)
    {
        if (schematic.writeToFile(dir, name, override))
        {
            parent.addMessage(MessageType.SUCCESS, "lmswap.info.saved", target.toString());
        }
        // On failure Litematica already shows its own error message
    }

    private static Path directoryFor(LitematicaSchematic schematic)
    {
        Path file = schematic.getFile();

        if (file != null && file.getParent() != null)
        {
            return file.getParent();
        }

        return DataManager.getSchematicsBaseDirectory();
    }

    static String suggestName(LitematicaSchematic schematic, Path dir)
    {
        String base = schematic.getFile() != null ? stripExtension(schematic.getFile().getFileName().toString())
                                                   : schematic.getMetadata().getName();
        String name = base + "_swapped";

        for (int i = 2; Files.exists(targetPath(dir, name)); ++i)
        {
            name = base + "_swapped_" + i;
        }

        return name;
    }

    /**
     * Same file name normalization as LitematicaSchematic.writeToFile(), so the existence check matches the file
     * that will actually be written.
     */
    private static Path targetPath(Path dir, String name)
    {
        String fileName = FileNameUtils.generateSimpleUnicodeSafeFileName(name);

        if (fileName.endsWith(EXTENSION) == false)
        {
            fileName = fileName + EXTENSION;
        }

        return dir.resolve(FileNameUtils.generateSafeFileName(fileName)).toAbsolutePath().normalize();
    }

    private static String stripExtension(String name)
    {
        return name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
    }
}
