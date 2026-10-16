package de.nils.lmswap.gui;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

/**
 * Scrollable, searchable list of target blocks. The search matches display name and block id.
 */
public class WidgetListReplaceTargets extends WidgetListBase<Block, WidgetReplaceTargetEntry>
{
    private final List<Block> targets;

    public WidgetListReplaceTargets(int x, int y, int width, int height, List<Block> targets,
                                    @Nullable ISelectionListener<Block> selectionListener)
    {
        super(x, y, width, height, selectionListener);

        this.targets = targets;
        this.browserEntryHeight = 22;
        this.browserEntriesOffsetY = 18;
        this.widgetSearchBar = new WidgetSearchBar(x + 2, y + 4, width - 14, 14, 0, Icons.FILE_ICON_SEARCH, LeftRight.LEFT);
        this.widgetSearchBar.setSearchOpen(true);
    }

    @Override
    protected Collection<Block> getAllEntries()
    {
        return this.targets;
    }

    @Override
    protected List<String> getEntryStringsForFilter(Block block)
    {
        return List.of(WidgetReplaceTargetEntry.displayName(block).toLowerCase(Locale.ROOT),
                       BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    @Override
    protected WidgetReplaceTargetEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, Block block)
    {
        return new WidgetReplaceTargetEntry(x, y, this.browserEntryWidth, this.getBrowserEntryHeightFor(block),
                                            isOdd, block, listIndex);
    }
}
