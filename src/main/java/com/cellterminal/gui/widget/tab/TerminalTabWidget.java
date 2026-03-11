package com.cellterminal.gui.widget.tab;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;

import com.cellterminal.client.CellInfo;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.client.TabStateManager;
import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.handler.TerminalDataManager;
import com.cellterminal.gui.rename.Renameable;
import com.cellterminal.gui.widget.CardsDisplay;
import com.cellterminal.gui.widget.DoubleClickTracker;
import com.cellterminal.gui.widget.IWidget;
import com.cellterminal.gui.widget.header.StorageHeader;
import com.cellterminal.gui.widget.line.TerminalLine;
import com.cellterminal.network.PacketEjectCell;
import com.cellterminal.network.PacketExtractUpgrade;
import com.cellterminal.config.CellTerminalServerConfig;


/**
 * Tab widget for the Terminal tab (Tab 0).
 * <p>
 * Displays storage groups with expandable cell lists. Each row is either:
 * <ul>
 *   <li>{@link StorageInfo} → {@link StorageHeader} (name, location, expand/collapse)</li>
 *   <li>{@link CellInfo} → {@link TerminalLine} (cell icon, name, usage bar, action buttons)</li>
 * </ul>
 *
 * The terminal tab provides per-cell action buttons (Eject, Inventory, Partition)
 * that navigate to other tabs or trigger server-side actions.
 */
public class TerminalTabWidget extends AbstractTabWidget {

    // ---- Hover preview state (updated during draw) ----

    /** The cell whose action button is currently hovered, or null if none */
    private CellInfo previewCell;

    /** Which button is hovered: 0=none, 1=inventory, 2=partition, 3=eject */
    private int previewType;

    public TerminalTabWidget(FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
    }

    // ---- Hover preview accessors ----

    /**
     * Get the cell whose action button is currently hovered (for popup preview).
     * Updated during each draw() call.
     */
    public CellInfo getPreviewCell() {
        return previewCell;
    }

    /**
     * Get which action button is hovered: 0=none, 1=inventory, 2=partition, 3=eject.
     */
    public int getPreviewType() {
        return previewType;
    }

    // ---- Drawing (with hover preview tracking) ----

    @Override
    public void draw(int mouseX, int mouseY) {
        // Reset preview state before drawing
        previewCell = null;
        previewType = 0;

        super.draw(mouseX, mouseY);

        // After drawing, scan visible TerminalLine widgets for hovered buttons
        for (IWidget widget : visibleRows) {
            if (!(widget instanceof TerminalLine)) continue;

            TerminalLine line = (TerminalLine) widget;
            int hoveredBtn = line.getHoveredButton();
            if (hoveredBtn != TerminalLine.HOVER_NONE) {
                Object data = widgetDataMap.get(widget);
                if (data instanceof CellInfo) {
                    previewCell = (CellInfo) data;
                    previewType = hoveredBtn;
                }
                break;
            }
        }
    }

    // ---- Row building ----

    @Override
    protected IWidget createRowWidget(Object lineData, int y, List<?> allLines, int lineIndex) {
        if (lineData instanceof StorageInfo) {
            return createStorageHeader((StorageInfo) lineData, y);
        }

        if (lineData instanceof CellInfo) {
            return createTerminalLine((CellInfo) lineData, y);
        }

        return null;
    }

    @Override
    protected boolean isContentLine(List<?> allLines, int index) {
        if (index < 0 || index >= allLines.size()) return false;

        return allLines.get(index) instanceof CellInfo;
    }

    // ---- Storage header creation ----

    private StorageHeader createStorageHeader(StorageInfo storage, int y) {
        StorageHeader header = new StorageHeader(y, fontRenderer, itemRender);
        header.setIconSupplier(storage::getBlockItem);
        header.setNameSupplier(storage::getName);
        header.setHasCustomNameSupplier(storage::hasCustomName);
        header.setLocationSupplier(storage::getLocationString);

        // Use TabStateManager for expand/collapse state (persists across rebuilds)
        header.setExpandedSupplier(() ->
            TabStateManager.getInstance().isExpanded(TabStateManager.TabType.TERMINAL, storage.getId()));

        header.setOnNameClick(() -> guiContext.startInlineRename(storage,
            y, getRenameFieldX(storage), getRenameFieldRightEdge(storage)));
        header.setOnNameDoubleClick(() -> guiContext.highlightInWorld(
            storage.getPos(), storage.getDimension(), storage.getName()),
            DoubleClickTracker.storageTargetId(storage.getId()));
        header.setOnExpandToggle(() -> {
            TabStateManager.getInstance().toggleExpanded(TabStateManager.TabType.TERMINAL, storage.getId());
            guiContext.rebuildAndUpdateScrollbar();
        });

        return header;
    }

    // ---- Terminal line creation ----

    private TerminalLine createTerminalLine(CellInfo cell, int y) {
        TerminalLine line = new TerminalLine(y, fontRenderer, itemRender);
        line.setCellItemSupplier(cell::getCellItem);
        line.setCellNameSupplier(cell::getDisplayName);
        line.setHasCustomNameSupplier(cell::hasCustomName);
        line.setByteUsageSupplier(cell::getByteUsagePercent);

        // Set target ID for double-click tracking (parent storage + slot = unique cell ID)
        line.setDoubleClickTargetId(DoubleClickTracker.cellTargetId(
            cell.getParentStorageId(), cell.getSlot()));

        // Create upgrade cards display
        CardsDisplay cards = createCellCards(cell, y);
        if (cards != null) line.setCardsDisplay(cards);

        // Wire up action callbacks directly to GuiContext
        line.setCallback(new TerminalLine.TerminalLineCallback() {
            @Override
            public void onEjectClicked() {
                guiContext.sendPacket(new PacketEjectCell(
                    cell.getParentStorageId(), cell.getSlot()));
            }

            @Override
            public void onInventoryClicked() {
                guiContext.openInventoryPopup(cell);
            }

            @Override
            public void onPartitionClicked() {
                guiContext.openPartitionPopup(cell);
            }

            @Override
            public void onNameClicked() {
                guiContext.startInlineRename(cell,
                    y, getRenameFieldX(cell), getRenameFieldRightEdge(cell));
            }

            @Override
            public void onNameDoubleClicked() {
                // Highlight the parent storage in-world
                guiContext.highlightCellInWorld(cell);
            }
        });

        return line;
    }

    // ---- Cards helper ----

    private CardsDisplay createCellCards(CellInfo cell, int rowY) {
        return createCellCardsDisplay(cell, rowY, this::handleCardClick);
    }

    private void handleCardClick(CellInfo cell, int upgradeSlotIndex) {
        if (CellTerminalServerConfig.isInitialized()
                && !CellTerminalServerConfig.getInstance().isUpgradeExtractEnabled()) {
            guiContext.showError("cellterminal.error.upgrade_extract_disabled");
            return;
        }

        boolean toInventory = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        guiContext.sendPacket(PacketExtractUpgrade.forCell(
            cell.getParentStorageId(), cell.getSlot(), upgradeSlotIndex, toInventory));
    }

    // ---- Tab controller methods ----

    @Override
    public List<Object> getLines(TerminalDataManager dataManager) {
        return dataManager.getLines();
    }

    @Override
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        return userSelectedMode;
    }

    @Override
    public boolean showSearchModeButton() {
        return true;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("gui.cellterminal.controls.double_click_storage_cell"));
        lines.add(I18n.format("gui.cellterminal.right_click_rename"));

        return lines;
    }

    @Override
    public ItemStack getTabIcon() {
        return AEApi.instance().definitions().parts().interfaceTerminal()
            .maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public String getTabTooltip() {
        return I18n.format("gui.cellterminal.tab.terminal.tooltip");
    }

    @Override
    protected Renameable resolveRenameable(Object data, int relMouseX) {
        if (data instanceof StorageInfo && relMouseX < GuiConstants.BUTTON_PARTITION_X) {
            return (StorageInfo) data;
        }

        if (data instanceof CellInfo && relMouseX < GuiConstants.BUTTON_EJECT_X) {
            return (CellInfo) data;
        }

        return null;
    }

    @Override
    public int getRenameFieldRightEdge(Renameable target) {
        // Cells on terminal tab have E/I/P buttons starting at BUTTON_EJECT_X
        if (target instanceof CellInfo) return GuiConstants.BUTTON_EJECT_X - 4;

        return super.getRenameFieldRightEdge(target);
    }
}
