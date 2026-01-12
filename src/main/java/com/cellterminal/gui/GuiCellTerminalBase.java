package com.cellterminal.gui;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;
import appeng.util.ReadableNumberConverter;

import mezz.jei.api.gui.IGhostIngredientHandler;

import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.CellTerminalClientConfig;
import com.cellterminal.client.EmptySlotInfo;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketEjectCell;
import com.cellterminal.network.PacketHighlightBlock;
import com.cellterminal.network.PacketInsertCell;
import com.cellterminal.network.PacketPartitionAction;
import com.cellterminal.network.PacketPickupCell;


/**
 * Base GUI for Cell Terminal variants.
 * Contains shared functionality for displaying storage drives/chests with their cells.
 * Supports three tabs: Terminal (list view), Inventory (cell slots with contents), Partition (cell slots with partition).
 *
 * NOTE: Tab 2 (Inventory) and Tab 3 (Partition) should mirror the behavior of
 * PopupCellInventory and PopupCellPartition respectively. When making changes,
 * refer to those classes for the expected behavior (item count placement, P indicator, JEI ghost support, etc).
 */
public abstract class GuiCellTerminalBase extends AEBaseGui implements IJEIGhostIngredients {

    // Tab constants
    public static final int TAB_TERMINAL = 0;
    public static final int TAB_INVENTORY = 1;
    public static final int TAB_PARTITION = 2;
    private static final int TAB_COUNT = 3;
    private static final int TAB_WIDTH = 22;
    private static final int TAB_HEIGHT = 22;
    private static final int TAB_Y_OFFSET = -22;

    protected static final int ROW_HEIGHT = 18;
    protected static final int GUI_INDENT = 22;
    protected static final int CELL_INDENT = GUI_INDENT + 12;
    protected static final int ROWS_VISIBLE = 8;

    // Button dimensions
    protected static final int BUTTON_SIZE = 14;
    protected static final int BUTTON_EJECT_X = 135;
    protected static final int BUTTON_INVENTORY_X = 150;
    protected static final int BUTTON_PARTITION_X = 165;

    // Slot constants for tab 2 and 3
    protected static final int SLOT_SIZE = 18;
    protected static final int SLOTS_PER_ROW = 8;
    protected static final int MAX_PARTITION_SLOTS = 63;

    protected final Map<Long, StorageInfo> storageMap = new LinkedHashMap<>();
    protected final List<Object> lines = new ArrayList<>();
    protected final List<Object> inventoryLines = new ArrayList<>();
    protected final List<Object> partitionLines = new ArrayList<>();

    // Current tab
    protected int currentTab;

    // Tab icons (lazy initialized)
    protected ItemStack tabIconTerminal = null;
    protected ItemStack tabIconInventory = null;
    protected ItemStack tabIconPartition = null;

    // Popup states
    protected PopupCellInventory inventoryPopup = null;
    protected PopupCellPartition partitionPopup = null;
    protected CellInfo hoveredCell = null;
    protected int hoverType = 0; // 0=none, 1=inventory, 2=partition, 3=eject

    // Tab hover for tooltips
    protected int hoveredTab = -1;

    // Hover tracking for background highlight
    protected int hoveredLineIndex = -1;

    // Double-click tracking
    protected long lastClickTime = 0;
    protected int lastClickedLineIndex = -1;

    // Terminal position for sorting (set by container)
    protected BlockPos terminalPos = BlockPos.ORIGIN;
    protected int terminalDimension = 0;

    // Tab 2/3 hover state
    protected CellInfo hoveredCellCell = null;
    protected StorageInfo hoveredCellStorage = null;
    protected int hoveredCellSlotIndex = -1;
    protected ItemStack hoveredContentStack = ItemStack.EMPTY;
    protected int hoveredContentX = 0;
    protected int hoveredContentY = 0;
    protected int hoveredContentSlotIndex = -1;

    // Partition slot tracking for JEI ghost ingredients and click handling
    protected int hoveredPartitionSlotIndex = -1;
    protected CellInfo hoveredPartitionCell = null;
    protected final List<PartitionSlotTarget> partitionSlotTargets = new ArrayList<>();



    // Drag state for cell reordering
    protected CellInfo draggedCell = null;
    protected StorageInfo draggedCellStorage = null;
    protected int draggedCellSlot = -1;

    public GuiCellTerminalBase(Container container) {
        super(container);

        this.xSize = 208;
        this.ySize = 222;

        GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);

        // Load persisted tab
        this.currentTab = CellTerminalClientConfig.getInstance().getSelectedTab();
        if (this.currentTab < 0 || this.currentTab >= TAB_COUNT) this.currentTab = TAB_TERMINAL;
    }

    protected abstract String getGuiTitle();

    @Override
    public void initGui() {
        super.initGui();
        this.getScrollBar().setTop(18).setLeft(189).setHeight(ROWS_VISIBLE * ROW_HEIGHT - 2);
        this.repositionSlots();
        initTabIcons();
    }

    protected void initTabIcons() {
        // Terminal icon - use the interface terminal part
        tabIconTerminal = AEApi.instance().definitions().parts().interfaceTerminal()
            .maybeStack(1).orElse(ItemStack.EMPTY);

        // Inventory icon - use ME Chest
        tabIconInventory = AEApi.instance().definitions().blocks().chest()
            .maybeStack(1).orElse(ItemStack.EMPTY);

        // Partition icon - use Cell Workbench
        tabIconPartition = AEApi.instance().definitions().blocks().cellWorkbench()
            .maybeStack(1).orElse(ItemStack.EMPTY);
    }

    protected void repositionSlots() {
        for (Object obj : this.inventorySlots.inventorySlots) {
            if (obj instanceof AppEngSlot) {
                AppEngSlot slot = (AppEngSlot) obj;
                slot.yPos = this.ySize + slot.getY() - 44;
                slot.xPos = slot.getX() + 14;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw popups on top (including their JEI ghost targets)
        if (inventoryPopup != null) {
            inventoryPopup.draw(mouseX, mouseY);
            inventoryPopup.drawTooltip(mouseX, mouseY);
        }

        if (partitionPopup != null) {
            partitionPopup.draw(mouseX, mouseY);
            partitionPopup.drawTooltip(mouseX, mouseY);
        }

        // Draw hover preview if hovering over button
        if (currentTab == TAB_TERMINAL && hoveredCell != null && inventoryPopup == null && partitionPopup == null) {
            int previewX = mouseX + 10;
            int previewY = mouseY + 10;

            if (hoverType == 1) {
                PopupCellInventory preview = new PopupCellInventory(this, hoveredCell, previewX, previewY);
                preview.draw(mouseX, mouseY);
            } else if (hoverType == 2) {
                PopupCellPartition preview = new PopupCellPartition(this, hoveredCell, previewX, previewY);
                preview.draw(mouseX, mouseY);
            } else if (hoverType == 3) {
                String ejectText = I18n.format("gui.cellterminal.eject_cell");
                List<String> tooltip = Collections.singletonList(ejectText);
                this.drawHoveringText(tooltip, mouseX, mouseY);
            }
        }

        // Draw tooltip for tab 2/3 content items
        if ((currentTab == TAB_INVENTORY || currentTab == TAB_PARTITION) && !hoveredContentStack.isEmpty()) {
            this.drawHoveringText(this.getItemToolTip(hoveredContentStack), hoveredContentX, hoveredContentY);
        }

        // Draw tab tooltips
        if (hoveredTab >= 0 && inventoryPopup == null && partitionPopup == null) {
            String tooltip = getTabTooltip(hoveredTab);
            if (!tooltip.isEmpty()) this.drawHoveringText(Collections.singletonList(tooltip), mouseX, mouseY);
        }
    }



    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(getGuiTitle(), 22, 6, 0x404040);
        String inventoryLabel = I18n.format("container.inventory");
        this.fontRenderer.drawString(inventoryLabel, 22, this.ySize - 58 + 3, 0x404040);

        // Reset hover states
        hoveredCell = null;
        hoverType = 0;
        hoveredLineIndex = -1;
        hoveredContentStack = ItemStack.EMPTY;
        hoveredCellCell = null;
        hoveredCellStorage = null;
        hoveredCellSlotIndex = -1;
        hoveredPartitionSlotIndex = -1;
        hoveredPartitionCell = null;

        // Clear partition slot targets for JEI ghost ingredients
        partitionSlotTargets.clear();

        // Draw based on current tab
        switch (currentTab) {
            case TAB_TERMINAL:
                drawTerminalTab(offsetX, offsetY, mouseX, mouseY);
                break;
            case TAB_INVENTORY:
                drawInventoryTab(offsetX, offsetY, mouseX, mouseY);
                break;
            case TAB_PARTITION:
                drawPartitionTab(offsetX, offsetY, mouseX, mouseY);
                break;
        }

    }

    protected void drawTerminalTab(int offsetX, int offsetY, int mouseX, int mouseY) {
        int y = 18;
        final int currentScroll = this.getScrollBar().getCurrentScroll();
        int relMouseX = mouseX - offsetX;
        int relMouseY = mouseY - offsetY;

        // Calculate visible area bounds for tree line clipping
        int visibleTop = 18;
        int visibleBottom = 18 + ROWS_VISIBLE * ROW_HEIGHT;
        int totalLines = lines.size();

        for (int i = 0; i < ROWS_VISIBLE && currentScroll + i < totalLines; i++) {
            Object line = lines.get(currentScroll + i);
            int lineIndex = currentScroll + i;

            // Check if mouse is hovering over this line
            boolean isHovered = relMouseX >= 4 && relMouseX < 185
                && relMouseY >= y && relMouseY < y + ROW_HEIGHT;

            // Draw hover background for cell lines
            if (isHovered && line instanceof CellInfo) {
                hoveredLineIndex = lineIndex;
                drawRect(GUI_INDENT, y - 1, 180, y + ROW_HEIGHT - 1, 0x50CCCCCC);
            }

            // Draw separator line above storage entries (except first one)
            if (line instanceof StorageInfo && i > 0) drawRect(GUI_INDENT, y - 1, 180, y, 0xFF606060);

            // Determine tree line extension parameters for cells
            boolean isFirstInGroup = isFirstInStorageGroupTerminal(lines, lineIndex);
            boolean isLastInGroup = isLastInStorageGroupTerminal(lines, lineIndex);
            boolean isFirstVisibleRow = (i == 0);
            boolean isLastVisibleRow = (i == ROWS_VISIBLE - 1) || (currentScroll + i == totalLines - 1);
            boolean hasContentAbove = (lineIndex > 0) && !isFirstInGroup;
            boolean hasContentBelow = (lineIndex < totalLines - 1) && !isLastInGroup;

            if (line instanceof StorageInfo) {
                drawStorageLine((StorageInfo) line, y);
            } else if (line instanceof CellInfo) {
                drawCellLine((CellInfo) line, y, relMouseX, relMouseY, isFirstInGroup, isLastInGroup, visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow);
            }

            y += ROW_HEIGHT;
        }
    }

    protected void drawInventoryTab(int offsetX, int offsetY, int mouseX, int mouseY) {
        int y = 18;
        final int currentScroll = this.getScrollBar().getCurrentScroll();
        int relMouseX = mouseX - offsetX;
        int relMouseY = mouseY - offsetY;

        // Calculate visible area bounds for tree line clipping
        int visibleTop = 18;
        int visibleBottom = 18 + ROWS_VISIBLE * ROW_HEIGHT;
        int totalLines = inventoryLines.size();

        for (int i = 0; i < ROWS_VISIBLE && currentScroll + i < totalLines; i++) {
            Object line = inventoryLines.get(currentScroll + i);
            int lineIndex = currentScroll + i;

            // Check if mouse is hovering over this line
            boolean isHovered = relMouseX >= 4 && relMouseX < 185
                && relMouseY >= y && relMouseY < y + ROW_HEIGHT;

            // Draw hover background for cell/empty slot lines
            if (isHovered && (line instanceof CellContentRow || line instanceof EmptySlotInfo)) {
                hoveredLineIndex = lineIndex;
                drawRect(GUI_INDENT, y - 1, 180, y + ROW_HEIGHT - 1, 0x50CCCCCC);
            }

            // Draw separator line above storage entries (except first one)
            if (line instanceof StorageInfo && i > 0) drawRect(GUI_INDENT, y - 1, 180, y, 0xFF606060);

            // Determine if this is the first/last row in storage group for tree line drawing
            boolean isFirstInGroup = isFirstInStorageGroup(inventoryLines, lineIndex);
            boolean isLastInGroup = isLastInStorageGroup(inventoryLines, lineIndex);

            // For visibility-based tree line extension:
            // - First visible row should extend up to visibleTop if there's content above (and not first in storage group)
            // - Last visible row should extend down to visibleBottom if there's content below (and not last in storage group)
            boolean isFirstVisibleRow = (i == 0);
            boolean isLastVisibleRow = (i == ROWS_VISIBLE - 1) || (currentScroll + i == totalLines - 1);
            boolean hasContentAbove = (lineIndex > 0) && !isFirstInGroup;
            boolean hasContentBelow = (lineIndex < totalLines - 1) && !isLastInGroup;

            if (line instanceof StorageInfo) {
                drawStorageLineSimple((StorageInfo) line, y);
            } else if (line instanceof CellContentRow) {
                CellContentRow row = (CellContentRow) line;
                drawCellInventoryLine(row.getCell(), row.getStartIndex(), row.isFirstRow(), y, relMouseX, relMouseY, mouseX, mouseY, isFirstInGroup, isLastInGroup, visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow);
            } else if (line instanceof EmptySlotInfo) {
                drawEmptySlotLine((EmptySlotInfo) line, y, relMouseX, relMouseY, isFirstInGroup, isLastInGroup, visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow);
            }

            y += ROW_HEIGHT;
        }
    }

    protected void drawPartitionTab(int offsetX, int offsetY, int mouseX, int mouseY) {
        int y = 18;
        final int currentScroll = this.getScrollBar().getCurrentScroll();
        int relMouseX = mouseX - offsetX;
        int relMouseY = mouseY - offsetY;

        // Calculate visible area bounds for tree line clipping
        int visibleTop = 18;
        int visibleBottom = 18 + ROWS_VISIBLE * ROW_HEIGHT;
        int totalLines = partitionLines.size();

        for (int i = 0; i < ROWS_VISIBLE && currentScroll + i < totalLines; i++) {
            Object line = partitionLines.get(currentScroll + i);
            int lineIndex = currentScroll + i;

            // Check if mouse is hovering over this line
            boolean isHovered = relMouseX >= 4 && relMouseX < 185
                && relMouseY >= y && relMouseY < y + ROW_HEIGHT;

            // Draw hover background for cell/empty slot lines
            if (isHovered && (line instanceof CellContentRow || line instanceof EmptySlotInfo)) {
                hoveredLineIndex = lineIndex;
                drawRect(GUI_INDENT, y - 1, 180, y + ROW_HEIGHT - 1, 0x50CCCCCC);
            }

            // Draw separator line above storage entries (except first one)
            if (line instanceof StorageInfo && i > 0) drawRect(GUI_INDENT, y - 1, 180, y, 0xFF606060);

            // Determine if this is the first/last row in storage group for tree line drawing
            boolean isFirstInGroup = isFirstInStorageGroup(partitionLines, lineIndex);
            boolean isLastInGroup = isLastInStorageGroup(partitionLines, lineIndex);

            // For visibility-based tree line extension:
            boolean isFirstVisibleRow = (i == 0);
            boolean isLastVisibleRow = (i == ROWS_VISIBLE - 1) || (currentScroll + i == totalLines - 1);
            boolean hasContentAbove = (lineIndex > 0) && !isFirstInGroup;
            boolean hasContentBelow = (lineIndex < totalLines - 1) && !isLastInGroup;

            if (line instanceof StorageInfo) {
                drawStorageLineSimple((StorageInfo) line, y);
            } else if (line instanceof CellContentRow) {
                CellContentRow row = (CellContentRow) line;
                drawCellPartitionLine(row.getCell(), row.getStartIndex(), row.isFirstRow(), y, relMouseX, relMouseY, mouseX, mouseY, isFirstInGroup, isLastInGroup, visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow);
            } else if (line instanceof EmptySlotInfo) {
                drawEmptySlotLine((EmptySlotInfo) line, y, relMouseX, relMouseY, isFirstInGroup, isLastInGroup, visibleTop, visibleBottom, isFirstVisibleRow, isLastVisibleRow, hasContentAbove, hasContentBelow);
            }

            y += ROW_HEIGHT;
        }
    }

    protected void drawStorageLineSimple(StorageInfo storage, int y) {
        // Draw vertical tree line connecting to cells below
        int lineX = GUI_INDENT + 7;
        drawRect(lineX, y + ROW_HEIGHT - 1, lineX + 1, y + ROW_HEIGHT, 0xFF808080);

        // Draw block icon
        if (!storage.getBlockItem().isEmpty()) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.enableGUIStandardItemLighting();
            this.itemRender.renderItemIntoGUI(storage.getBlockItem(), GUI_INDENT, y);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        // Draw name and location
        String name = storage.getName();
        if (name.length() > 20) name = name.substring(0, 18) + "...";
        this.fontRenderer.drawString(name, GUI_INDENT + 20, y + 1, 0x404040);

        String location = storage.getLocationString();
        this.fontRenderer.drawString(location, GUI_INDENT + 20, y + 9, 0x808080);
    }

    protected void drawCellInventoryLine(CellInfo cell, int startIndex, boolean isFirstRow, int y, int mouseX, int mouseY, int absMouseX, int absMouseY, boolean isFirstInGroup, boolean isLastInGroup, int visibleTop, int visibleBottom, boolean isFirstVisibleRow, boolean isLastVisibleRow, boolean hasContentAbove, boolean hasContentBelow) {
        int lineX = GUI_INDENT + 7;

        // Only draw tree line and cell icon on first row
        if (isFirstRow) {
            // Draw tree line with proper bounds, clamped to visible area
            // Top of line: connect up to storage header or previous cell
            int lineTop;
            if (isFirstInGroup) {
                // First cell in group: connect up to the storage header
                if (isFirstVisibleRow) {
                    lineTop = visibleTop;
                } else {
                    lineTop = y - ROW_HEIGHT + 9; // Connect to storage header above
                }
            } else if (isFirstVisibleRow && hasContentAbove) {
                lineTop = visibleTop; // Extend to top border when there's content above
            } else {
                lineTop = y - ROW_HEIGHT + 9;
            }

            // Bottom of line: if last in storage group, end at horizontal branch level
            // Otherwise, extend downward (to visible bottom if last visible row with more content below)
            int lineBottom;
            if (isLastInGroup) {
                lineBottom = y + 9;
            } else if (isLastVisibleRow && hasContentBelow) {
                lineBottom = visibleBottom; // Extend to bottom border when there's content below
            } else {
                lineBottom = y + ROW_HEIGHT;
            }

            drawRect(lineX, lineTop, lineX + 1, lineBottom, 0xFF808080);
            drawRect(lineX, y + 8, lineX + 10, y + 9, 0xFF808080);

            // Draw cell slot background
            drawSlotBackground(CELL_INDENT, y);

            // Check if mouse is over cell slot
            boolean cellHovered = mouseX >= CELL_INDENT && mouseX < CELL_INDENT + 16
                    && mouseY >= y && mouseY < y + 16;

            if (cellHovered) {
                drawRect(CELL_INDENT + 1, y + 1, CELL_INDENT + 15, y + 15, 0x80FFFFFF);
                StorageInfo storage = storageMap.get(cell.getParentStorageId());
                if (storage != null) {
                    hoveredCellStorage = storage;
                    hoveredCellCell = cell;
                    hoveredCellSlotIndex = cell.getSlot();
                    hoveredContentStack = cell.getCellItem();
                    hoveredContentX = absMouseX;
                    hoveredContentY = absMouseY;
                }
            }

            // Draw cell icon
            if (!cell.getCellItem().isEmpty()) {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                RenderHelper.enableGUIStandardItemLighting();
                this.itemRender.renderItemIntoGUI(cell.getCellItem(), CELL_INDENT, y);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableLighting();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        } else {
            // Draw continuation tree line for subsequent rows
            int lineTop;
            if (isFirstVisibleRow && hasContentAbove) {
                lineTop = visibleTop;
            } else {
                lineTop = y - ROW_HEIGHT + 9;
            }

            int lineBottom;
            if (isLastInGroup) {
                lineBottom = y + 9;
            } else if (isLastVisibleRow && hasContentBelow) {
                lineBottom = visibleBottom;
            } else {
                lineBottom = y + ROW_HEIGHT;
            }
            drawRect(lineX, lineTop, lineX + 1, lineBottom, 0xFF808080);
        }

        // Draw content item slots for this row
        List<ItemStack> contents = cell.getContents();
        List<ItemStack> partition = cell.getPartition();
        int slotStartX = CELL_INDENT + 20;

        for (int i = 0; i < SLOTS_PER_ROW; i++) {
            int contentIndex = startIndex + i;
            int slotX = slotStartX + (i * 16);
            int slotY = y;

            // Draw mini slot background
            drawRect(slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
            drawRect(slotX, slotY, slotX + 15, slotY + 1, 0xFF373737);
            drawRect(slotX, slotY, slotX + 1, slotY + 15, 0xFF373737);
            drawRect(slotX + 1, slotY + 15, slotX + 16, slotY + 16, 0xFFFFFFFF);
            drawRect(slotX + 15, slotY + 1, slotX + 16, slotY + 15, 0xFFFFFFFF);

            if (contentIndex < contents.size() && !contents.get(contentIndex).isEmpty()) {
                ItemStack stack = contents.get(contentIndex);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                RenderHelper.enableGUIStandardItemLighting();
                this.itemRender.renderItemIntoGUI(stack, slotX, slotY);
                RenderHelper.disableStandardItemLighting();

                // Draw "P" indicator if this item is in partition
                if (isInPartition(stack, partition)) {
                    GlStateManager.disableLighting();
                    GlStateManager.disableDepth();
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(0.5f, 0.5f, 0.5f);
                    this.fontRenderer.drawStringWithShadow("P", (slotX + 1) * 2, (slotY + 1) * 2, 0xFF55FF55);
                    GlStateManager.popMatrix();
                    GlStateManager.enableDepth();
                }

                // Draw item count on item corner (bottom-right)
                String countStr = formatItemCount(cell.getContentCount(contentIndex));
                int countWidth = this.fontRenderer.getStringWidth(countStr);
                GlStateManager.disableDepth();
                GlStateManager.pushMatrix();
                GlStateManager.scale(0.5f, 0.5f, 0.5f);
                // Right-align: for 16x16 slot, text right edge
                this.fontRenderer.drawStringWithShadow(countStr, (slotX + 15) * 2 - countWidth, (slotY + 11) * 2, 0xFFFFFF);
                GlStateManager.popMatrix();
                GlStateManager.enableDepth();

                // Check hover for tooltip and click-to-toggle tracking
                if (mouseX >= slotX && mouseX < slotX + 16
                        && mouseY >= slotY && mouseY < slotY + 16) {
                    drawRect(slotX + 1, slotY + 1, slotX + 15, slotY + 15, 0x80FFFFFF);
                    hoveredContentStack = stack;
                    hoveredContentX = absMouseX;
                    hoveredContentY = absMouseY;
                    hoveredContentSlotIndex = contentIndex;
                    hoveredCellCell = cell;
                }
            }
        }

    }

    protected boolean isInPartition(ItemStack stack, List<ItemStack> partition) {
        if (stack.isEmpty()) return false;

        for (ItemStack partItem : partition) {
            if (ItemStack.areItemsEqual(stack, partItem)) return true;
        }

        return false;
    }

    protected void drawCellPartitionLine(CellInfo cell, int startIndex, boolean isFirstRow, int y, int mouseX, int mouseY, int absMouseX, int absMouseY, boolean isFirstInGroup, boolean isLastInGroup, int visibleTop, int visibleBottom, boolean isFirstVisibleRow, boolean isLastVisibleRow, boolean hasContentAbove, boolean hasContentBelow) {
        int lineX = GUI_INDENT + 7;

        // Only draw tree line and cell icon on first row
        if (isFirstRow) {
            // Draw tree line with proper bounds, clamped to visible area
            int lineTop;
            if (isFirstInGroup) {
                // First cell in group: connect up to the storage header
                if (isFirstVisibleRow) {
                    lineTop = visibleTop;
                } else {
                    lineTop = y - ROW_HEIGHT + 9; // Connect to storage header above
                }
            } else if (isFirstVisibleRow && hasContentAbove) {
                lineTop = visibleTop;
            } else {
                lineTop = y - ROW_HEIGHT + 9;
            }

            int lineBottom;
            if (isLastInGroup) {
                lineBottom = y + 9;
            } else if (isLastVisibleRow && hasContentBelow) {
                lineBottom = visibleBottom;
            } else {
                lineBottom = y + ROW_HEIGHT;
            }

            drawRect(lineX, lineTop, lineX + 1, lineBottom, 0xFF808080);
            drawRect(lineX, y + 8, lineX + 10, y + 9, 0xFF808080);

            // Draw cell slot background
            drawSlotBackground(CELL_INDENT, y);

            // Check if mouse is over cell slot
            boolean cellHovered = mouseX >= CELL_INDENT && mouseX < CELL_INDENT + 16
                    && mouseY >= y && mouseY < y + 16;

            if (cellHovered) {
                drawRect(CELL_INDENT + 1, y + 1, CELL_INDENT + 15, y + 15, 0x80FFFFFF);
                StorageInfo storage = storageMap.get(cell.getParentStorageId());
                if (storage != null) {
                    hoveredCellStorage = storage;
                    hoveredCellCell = cell;
                    hoveredCellSlotIndex = cell.getSlot();
                    hoveredContentStack = cell.getCellItem();
                    hoveredContentX = absMouseX;
                    hoveredContentY = absMouseY;
                }
            }

            // Draw cell icon
            if (!cell.getCellItem().isEmpty()) {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                RenderHelper.enableGUIStandardItemLighting();
                this.itemRender.renderItemIntoGUI(cell.getCellItem(), CELL_INDENT, y);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableLighting();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        } else {
            // Draw continuation tree line for subsequent rows
            int lineTop;
            if (isFirstVisibleRow && hasContentAbove) {
                lineTop = visibleTop;
            } else {
                lineTop = y - ROW_HEIGHT + 9;
            }

            int lineBottom;
            if (isLastInGroup) {
                lineBottom = y + 9;
            } else if (isLastVisibleRow && hasContentBelow) {
                lineBottom = visibleBottom;
            } else {
                lineBottom = y + ROW_HEIGHT;
            }
            drawRect(lineX, lineTop, lineX + 1, lineBottom, 0xFF808080);
        }

        // Draw partition item slots for this row
        // Use orange-ish tint for partition slots to differentiate from content slots
        List<ItemStack> partitions = cell.getPartition();
        int slotStartX = CELL_INDENT + 20;

        for (int i = 0; i < SLOTS_PER_ROW; i++) {
            int partitionIndex = startIndex + i;

            // Stop if we've reached the maximum partition slots (8th row has only 7 slots)
            if (partitionIndex >= MAX_PARTITION_SLOTS) break;

            int slotX = slotStartX + (i * 16);
            int slotY = y;

            // Draw mini slot background with subtle orange/amber tint for partition
            drawRect(slotX, slotY, slotX + 16, slotY + 16, 0xFF9B8B7B);
            drawRect(slotX, slotY, slotX + 15, slotY + 1, 0xFF473737);
            drawRect(slotX, slotY, slotX + 1, slotY + 15, 0xFF473737);
            drawRect(slotX + 1, slotY + 15, slotX + 16, slotY + 16, 0xFFFFEEDD);
            drawRect(slotX + 15, slotY + 1, slotX + 16, slotY + 15, 0xFFFFEEDD);

            // Track slot position for JEI ghost ingredients (absolute screen coordinates)
            int absSlotX = this.guiLeft + slotX;
            int absSlotY = this.guiTop + slotY;
            partitionSlotTargets.add(new PartitionSlotTarget(cell, partitionIndex, absSlotX, absSlotY, 16, 16));

            boolean slotHovered = mouseX >= slotX && mouseX < slotX + 16
                    && mouseY >= slotY && mouseY < slotY + 16;

            if (partitionIndex < partitions.size() && !partitions.get(partitionIndex).isEmpty()) {
                ItemStack stack = partitions.get(partitionIndex);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                RenderHelper.enableGUIStandardItemLighting();
                this.itemRender.renderItemIntoGUI(stack, slotX, slotY);
                RenderHelper.disableStandardItemLighting();

                // Check hover for tooltip and click-to-remove tracking
                if (slotHovered) {
                    drawRect(slotX + 1, slotY + 1, slotX + 15, slotY + 15, 0x80FFFFFF);
                    hoveredContentStack = stack;
                    hoveredContentX = absMouseX;
                    hoveredContentY = absMouseY;
                    hoveredPartitionSlotIndex = partitionIndex;
                    hoveredPartitionCell = cell;
                }
            } else if (slotHovered) {
                // Empty slot hover - track for JEI ghost ingredient
                drawRect(slotX + 1, slotY + 1, slotX + 15, slotY + 15, 0x40FFFFFF);
                hoveredPartitionSlotIndex = partitionIndex;
                hoveredPartitionCell = cell;
            }
        }

    }

    protected void drawSlotBackground(int x, int y) {
        // Standard Minecraft-style slot background
        drawRect(x, y, x + 16, y + 16, 0xFF8B8B8B);
        drawRect(x, y, x + 15, y + 1, 0xFF373737);
        drawRect(x, y, x + 1, y + 15, 0xFF373737);
        drawRect(x + 1, y + 15, x + 16, y + 16, 0xFFFFFFFF);
        drawRect(x + 15, y + 1, x + 16, y + 15, 0xFFFFFFFF);
    }

    protected void drawEmptySlotLine(EmptySlotInfo emptySlot, int y, int mouseX, int mouseY, boolean isFirstInGroup, boolean isLastInGroup, int visibleTop, int visibleBottom, boolean isFirstVisibleRow, boolean isLastVisibleRow, boolean hasContentAbove, boolean hasContentBelow) {
        // Draw tree line with proper bounds, clamped to visible area
        int lineX = GUI_INDENT + 7;

        int lineTop;
        if (isFirstInGroup) {
            // First entry in group: connect up to the storage header
            if (isFirstVisibleRow) {
                lineTop = visibleTop;
            } else {
                lineTop = y - ROW_HEIGHT + 9; // Connect to storage header above
            }
        } else if (isFirstVisibleRow && hasContentAbove) {
            lineTop = visibleTop;
        } else {
            lineTop = y - ROW_HEIGHT + 9;
        }

        int lineBottom;
        if (isLastInGroup) {
            lineBottom = y + 9;
        } else if (isLastVisibleRow && hasContentBelow) {
            lineBottom = visibleBottom;
        } else {
            lineBottom = y + ROW_HEIGHT;
        }

        drawRect(lineX, lineTop, lineX + 1, lineBottom, 0xFF808080);
        drawRect(lineX, y + 8, lineX + 10, y + 9, 0xFF808080);

        // Draw empty slot with proper slot background
        drawSlotBackground(CELL_INDENT, y);

        // Check if mouse is over slot
        boolean slotHovered = mouseX >= CELL_INDENT && mouseX < CELL_INDENT + 16
                && mouseY >= y && mouseY < y + 16;

        if (slotHovered) {
            drawRect(CELL_INDENT + 1, y + 1, CELL_INDENT + 15, y + 15, 0x80FFFFFF);
            StorageInfo storage = storageMap.get(emptySlot.getParentStorageId());
            if (storage != null) {
                hoveredCellStorage = storage;
                hoveredCellSlotIndex = emptySlot.getSlot();
            }
        }
    }

    protected String formatItemCount(long count) {
        if (count <= 1) return "";
        if (count < 1000) return String.valueOf(count);

        return ReadableNumberConverter.INSTANCE.toSlimReadableForm(count);
    }

    /**
     * Check if the line at the given index is the first cell/slot in its storage group.
     * Returns true if the previous line is a StorageInfo or if this is the first line.
     */
    protected boolean isFirstInStorageGroup(List<Object> lines, int index) {
        if (index <= 0) return true;

        Object prevLine = lines.get(index - 1);

        return prevLine instanceof StorageInfo;
    }

    /**
     * Check if the line at the given index is the last cell/slot in its storage group.
     * Returns true if the next line is a StorageInfo or if this is the last line.
     */
    protected boolean isLastInStorageGroup(List<Object> lines, int index) {
        if (index >= lines.size() - 1) return true;

        Object nextLine = lines.get(index + 1);

        return nextLine instanceof StorageInfo;
    }

    /**
     * Check if the line at the given index is the first cell in its storage group (Terminal tab).
     * Returns true if the previous line is a StorageInfo or if this is the first line.
     */
    protected boolean isFirstInStorageGroupTerminal(List<Object> lines, int index) {
        if (index <= 0) return true;

        Object prevLine = lines.get(index - 1);

        return prevLine instanceof StorageInfo;
    }

    /**
     * Check if the line at the given index is the last cell in its storage group (Terminal tab).
     * Returns true if the next line is a StorageInfo or if this is the last line.
     */
    protected boolean isLastInStorageGroupTerminal(List<Object> lines, int index) {
        if (index >= lines.size() - 1) return true;

        Object nextLine = lines.get(index + 1);

        return nextLine instanceof StorageInfo;
    }

    protected void drawStorageLine(StorageInfo storage, int y) {
        // Draw expand/collapse indicator on the right
        String expandIcon = storage.isExpanded() ? "[-]" : "[+]";
        this.fontRenderer.drawString(expandIcon, 165, y + 6, 0x606060);

        // Draw vertical tree line connecting to cells below (if expanded and has slots)
        if (storage.isExpanded() && storage.getSlotCount() > 0) {
            int lineX = GUI_INDENT + 7;
            // Draw from bottom of this row down to connect with cells
            drawRect(lineX, y + ROW_HEIGHT - 1, lineX + 1, y + ROW_HEIGHT, 0xFF808080);
        }

        // Draw block icon
        if (!storage.getBlockItem().isEmpty()) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.enableGUIStandardItemLighting();
            this.itemRender.renderItemIntoGUI(storage.getBlockItem(), GUI_INDENT, y);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        // Draw name and location
        String name = storage.getName();
        if (name.length() > 20) name = name.substring(0, 18) + "...";
        this.fontRenderer.drawString(name, GUI_INDENT + 20, y + 1, 0x404040);

        String location = storage.getLocationString();
        this.fontRenderer.drawString(location, GUI_INDENT + 20, y + 9, 0x808080);
    }

    /**
     * Get the number of characters used for decorations (§ codes) in a string.
     * @param name The string to check
     * @return The length of decoration codes
     */
    protected int getDecorationLength(String name) {
        int decorLength = 0;

        for (int i = 0; i < name.length() - 1; i++) {
            if (name.charAt(i) == '§') {
                decorLength += 2;
                i++;
            }
        }

        return decorLength;
    }

    protected void drawCellLine(CellInfo cell, int y, int mouseX, int mouseY, boolean isFirstInGroup, boolean isLastInGroup, int visibleTop, int visibleBottom, boolean isFirstVisibleRow, boolean isLastVisibleRow, boolean hasContentAbove, boolean hasContentBelow) {
        // Draw tree line to show hierarchy
        int lineX = GUI_INDENT + 7;

        // Calculate tree line top position
        int lineTop;
        if (isFirstInGroup) {
            // First cell in group: connect up to the storage header
            // If first visible row, only draw from visible top
            if (isFirstVisibleRow) {
                lineTop = visibleTop;
            } else {
                lineTop = y - ROW_HEIGHT + 9; // Connect to storage header above
            }
        } else if (isFirstVisibleRow && hasContentAbove) {
            lineTop = visibleTop; // Extend to top border when there's content above
        } else {
            lineTop = y - ROW_HEIGHT + 9; // Connect to previous row
        }

        // Calculate tree line bottom position
        int lineBottom;
        if (isLastInGroup) {
            lineBottom = y + 9; // End at horizontal branch level
        } else if (isLastVisibleRow && hasContentBelow) {
            lineBottom = visibleBottom; // Extend to bottom border when there's content below
        } else {
            lineBottom = y + ROW_HEIGHT; // Connect to next row
        }

        drawRect(lineX, lineTop, lineX + 1, lineBottom, 0xFF808080);
        drawRect(lineX, y + 8, lineX + 10, y + 9, 0xFF808080);

        // Draw cell icon with indent
        if (!cell.getCellItem().isEmpty()) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.enableGUIStandardItemLighting();
            this.itemRender.renderItemIntoGUI(cell.getCellItem(), CELL_INDENT, y);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        // Draw cell name
        String name = cell.getDisplayName();
        int decorLength = getDecorationLength(name);
        if (name.length() - decorLength > 16) name = name.substring(0, 14 + decorLength) + "...";
        this.fontRenderer.drawString(name, CELL_INDENT + 18, y + 1, 0x404040);

        // Draw usage bar
        int barX = CELL_INDENT + 18;
        int barY = y + 10;
        int barWidth = 80;
        int barHeight = 4;

        drawRect(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);
        int filledWidth = (int) (barWidth * cell.getByteUsagePercent());
        int fillColor = getUsageColor(cell.getByteUsagePercent());
        if (filledWidth > 0) drawRect(barX, barY, barX + filledWidth, barY + barHeight, fillColor);

        // Check button hover states
        boolean ejectHovered = mouseX >= BUTTON_EJECT_X && mouseX < BUTTON_EJECT_X + BUTTON_SIZE
            && mouseY >= y + 1 && mouseY < y + 1 + BUTTON_SIZE;
        boolean invHovered = mouseX >= BUTTON_INVENTORY_X && mouseX < BUTTON_INVENTORY_X + BUTTON_SIZE
            && mouseY >= y + 1 && mouseY < y + 1 + BUTTON_SIZE;
        boolean partHovered = mouseX >= BUTTON_PARTITION_X && mouseX < BUTTON_PARTITION_X + BUTTON_SIZE
            && mouseY >= y + 1 && mouseY < y + 1 + BUTTON_SIZE;

        // Draw buttons
        drawButton(BUTTON_EJECT_X, y + 1, "E", ejectHovered);
        drawButton(BUTTON_INVENTORY_X, y + 1, "I", invHovered);
        drawButton(BUTTON_PARTITION_X, y + 1, "P", partHovered);

        // Track hover state for preview/tooltip
        if (ejectHovered) {
            hoveredCell = cell;
            hoverType = 3;
        } else if (invHovered) {
            hoveredCell = cell;
            hoverType = 1;
        } else if (partHovered) {
            hoveredCell = cell;
            hoverType = 2;
        }
    }

    protected void drawButton(int x, int y, String label, boolean hovered) {
        int btnColor = hovered ? 0xFF707070 : 0xFF8B8B8B;
        drawRect(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, btnColor);
        drawRect(x, y, x + BUTTON_SIZE, y + 1, 0xFFFFFFFF);
        drawRect(x, y, x + 1, y + BUTTON_SIZE, 0xFFFFFFFF);
        drawRect(x, y + BUTTON_SIZE - 1, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFF555555);
        drawRect(x + BUTTON_SIZE - 1, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFF555555);
        this.fontRenderer.drawString(label, x + 4, y + 3, 0x404040);
    }

    protected int getUsageColor(float percent) {
        if (percent > 0.9f) return 0xFFFF3333;
        if (percent > 0.75f) return 0xFFFFAA00;

        return 0xFF33FF33;
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();

        this.bindTexture("guis/newinterfaceterminal.png");

        // Draw top section
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, 18);

        // Draw middle section (repeated rows)
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            this.drawTexturedModalRect(offsetX, offsetY + 18 + i * ROW_HEIGHT, 0, 52, this.xSize, ROW_HEIGHT);
        }

        // Draw upper border for the main area (as the texture has a gap)
        drawRect(offsetX + 21, offsetY + 17, offsetX + 182, offsetY + 18, 0xFF373737);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw bottom section
        int bottomY = offsetY + 18 + ROWS_VISIBLE * ROW_HEIGHT;
        this.drawTexturedModalRect(offsetX, bottomY, 0, 158, this.xSize, 98);

        drawTabs(offsetX, offsetY, mouseX, mouseY);
    }

    protected void drawTabs(int offsetX, int offsetY, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();

        int tabY = offsetY + TAB_Y_OFFSET;
        hoveredTab = -1;

        for (int i = 0; i < 3; i++) {
            int tabX = offsetX + 4 + (i * (TAB_WIDTH + 2));
            boolean isSelected = (i == currentTab);
            boolean isHovered = mouseX >= tabX && mouseX < tabX + TAB_WIDTH
                && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            if (isHovered) hoveredTab = i;

            // Tab background
            int bgColor = isSelected ? 0xFFC6C6C6 : (isHovered ? 0xFFA0A0A0 : 0xFF8B8B8B);
            drawRect(tabX, tabY, tabX + TAB_WIDTH, tabY + TAB_HEIGHT, bgColor);

            // Tab border (3D effect)
            drawRect(tabX, tabY, tabX + TAB_WIDTH, tabY + 1, 0xFFFFFFFF);  // top
            drawRect(tabX, tabY, tabX + 1, tabY + TAB_HEIGHT, 0xFFFFFFFF);  // left
            drawRect(tabX + TAB_WIDTH - 1, tabY, tabX + TAB_WIDTH, tabY + TAB_HEIGHT, 0xFF555555);  // right

            // If selected, remove bottom border to connect with main GUI
            if (isSelected) {
                drawRect(tabX + 1, tabY + TAB_HEIGHT - 1, tabX + TAB_WIDTH - 1, tabY + TAB_HEIGHT, 0xFFC6C6C6);
            } else {
                drawRect(tabX, tabY + TAB_HEIGHT - 1, tabX + TAB_WIDTH, tabY + TAB_HEIGHT, 0xFF555555);  // bottom
            }

            // Draw icon
            ItemStack icon = getTabIcon(i);
            if (!icon.isEmpty()) {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                RenderHelper.enableGUIStandardItemLighting();
                this.itemRender.renderItemIntoGUI(icon, tabX + 3, tabY + 3);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableLighting();
            }
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
    }

    protected String getTabTooltip(int tab) {
        switch (tab) {
            case TAB_TERMINAL:
                return I18n.format("gui.cellterminal.tab.terminal.tooltip");
            case TAB_INVENTORY:
                return I18n.format("gui.cellterminal.tab.inventory.tooltip");
            case TAB_PARTITION:
                return I18n.format("gui.cellterminal.tab.partition.tooltip");
            default:
                return "";
        }
    }

    protected ItemStack getTabIcon(int tab) {
        switch (tab) {
            case TAB_TERMINAL:
                return tabIconTerminal;
            case TAB_INVENTORY:
                return tabIconInventory;
            case TAB_PARTITION:
                return tabIconPartition;
            default:
                return ItemStack.EMPTY;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Handle popup clicks first
        if (inventoryPopup != null) {
            if (inventoryPopup.handleClick(mouseX, mouseY, mouseButton)) return;

            // Click outside popup closes it
            inventoryPopup = null;

            return;
        }

        if (partitionPopup != null) {
            if (partitionPopup.handleClick(mouseX, mouseY, mouseButton)) return;

            // Click outside popup closes it
            partitionPopup = null;

            return;
        }

        // Check tab clicks
        if (handleTabClick(mouseX, mouseY)) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);

        // Handle tab-specific clicks
        if (currentTab == TAB_TERMINAL) {
            handleTerminalTabClick(mouseX, mouseY, mouseButton);
        } else if (currentTab == TAB_INVENTORY || currentTab == TAB_PARTITION) {
            handleCellTabClick(mouseX, mouseY, mouseButton);
        }
    }

    protected boolean handleTabClick(int mouseX, int mouseY) {
        int tabY = this.guiTop + TAB_Y_OFFSET;

        for (int i = 0; i < 3; i++) {
            int tabX = this.guiLeft + 4 + (i * (TAB_WIDTH + 2));

            if (mouseX >= tabX && mouseX < tabX + TAB_WIDTH
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                if (currentTab != i) {
                    currentTab = i;
                    // Reset scroll position to 0, then update range for new tab
                    this.getScrollBar().setRange(0, 0, 1);
                    updateScrollbarForCurrentTab();
                    CellTerminalClientConfig.getInstance().setSelectedTab(i);
                }

                return true;
            }
        }

        return false;
    }

    protected void handleTerminalTabClick(int mouseX, int mouseY, int mouseButton) {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        if (relX < 4 || relX > 190 || relY < 18 || relY >= 18 + ROWS_VISIBLE * ROW_HEIGHT) return;

        int row = (relY - 18) / ROW_HEIGHT;
        int lineIndex = this.getScrollBar().getCurrentScroll() + row;

        if (lineIndex >= lines.size()) return;

        Object line = lines.get(lineIndex);

        // Check for held cell - insert into storage
        ItemStack heldStack = this.mc.player.inventory.getItemStack();
        if (!heldStack.isEmpty()) {
            long storageId = -1;

            if (line instanceof StorageInfo) {
                storageId = ((StorageInfo) line).getId();
            } else if (line instanceof CellInfo) {
                storageId = ((CellInfo) line).getParentStorageId();
            }

            if (storageId >= 0) {
                CellTerminalNetwork.INSTANCE.sendToServer(new PacketInsertCell(storageId, -1));

                return;
            }
        }

        // Check for double-click to highlight block
        long now = System.currentTimeMillis();
        if (lineIndex == lastClickedLineIndex && now - lastClickTime < 400) {
            // Double-click detected
            handleDoubleClick(line);
            lastClickedLineIndex = -1;

            return;
        }

        lastClickedLineIndex = lineIndex;
        lastClickTime = now;

        if (line instanceof StorageInfo) {
            StorageInfo storage = (StorageInfo) line;

            // Only toggle expand when clicking on the [+]/[-] button (around x=165)
            if (relX >= 165 && relX < 180) {
                storage.toggleExpanded();
                rebuildLines();
            }

            return;
        }

        if (line instanceof CellInfo) {
            handleCellClick((CellInfo) line, relX, relY, row, mouseX, mouseY);
        }
    }

    // TODO: Expose cell slots as real inventory slots for Mouse Tweaks compatibility.
    //
    // Implementation approach:
    // 1. Create a SlotCellStorage class extending SlotFake (see appeng.container.slot.SlotFake)
    //    - Store storageId and slotIndex instead of IItemHandler reference
    //    - Override click handling to send PacketInsertCell/PacketEjectCell
    //    - Override getStack() to return the cell ItemStack from client-side StorageInfo
    //
    // 2. In ContainerCellTerminalBase:
    //    - Add a List<SlotCellStorage> cellSlots field
    //    - After regenStorageList(), clear old slots and add new ones based on storage data
    //    - Use addSlotToContainer() but position them off-screen initially
    //    - Send slot positions to client as part of PacketCellTerminalUpdate
    //
    // 3. In this GUI class:
    //    - In drawInventoryTab/drawPartitionTab, update slot.xPos/yPos to match drawn positions
    //    - Set slots off-screen when not on their respective tab (xPos = -9999)
    //    - Let Minecraft's normal slot handling take over click events
    //
    // 4. For Mouse Tweaks specifically:
    //    - Ensure slots implement IMouseTweaksDisableWheelTweak if needed
    //    - Test with both insert (holding cell) and eject (clicking cell) operations
    //
    // Challenges:
    // - Slots are normally static in containers; dynamic add/remove requires care
    // - Client/server sync of slot positions and contents must be handled
    // - May conflict with player inventory slot indices
    protected void handleCellTabClick(int mouseX, int mouseY, int mouseButton) {
        // Tab 2 (Inventory): Check if clicking on a content item to toggle partition
        if (currentTab == TAB_INVENTORY && hoveredCellCell != null && hoveredContentSlotIndex >= 0) {
            List<ItemStack> contents = hoveredCellCell.getContents();

            if (hoveredContentSlotIndex < contents.size() && !contents.get(hoveredContentSlotIndex).isEmpty()) {
                // Click on content item - toggle its partition status
                onTogglePartitionItem(hoveredCellCell, contents.get(hoveredContentSlotIndex));

                return;
            }
        }

        // Tab 3 (Partition): Check if clicking on a partition slot
        if (currentTab == TAB_PARTITION && hoveredPartitionCell != null && hoveredPartitionSlotIndex >= 0) {
            List<ItemStack> partitions = hoveredPartitionCell.getPartition();
            ItemStack heldStack = this.mc.player.inventory.getItemStack();
            boolean slotOccupied = hoveredPartitionSlotIndex < partitions.size() && !partitions.get(hoveredPartitionSlotIndex).isEmpty();

            // If holding an item, replace/add to the partition slot
            if (!heldStack.isEmpty()) {
                onAddPartitionItem(hoveredPartitionCell, hoveredPartitionSlotIndex, heldStack);

                return;
            }

            // If not holding an item and slot is occupied, remove it
            if (slotOccupied) {
                onRemovePartitionItem(hoveredPartitionCell, hoveredPartitionSlotIndex);

                return;
            }
        }

        // Handle cell slot clicks (pickup/swap cells)
        if (hoveredCellStorage == null || hoveredCellSlotIndex < 0) return;

        // Use PacketPickupCell for slot-like behavior (pickup/swap cells)
        CellTerminalNetwork.INSTANCE.sendToServer(
            new PacketPickupCell(hoveredCellStorage.getId(), hoveredCellSlotIndex)
        );
    }

    protected void handleDoubleClick(Object line) {
        StorageInfo storage = null;

        if (line instanceof StorageInfo) {
            storage = (StorageInfo) line;
        } else if (line instanceof CellInfo) {
            CellInfo cell = (CellInfo) line;
            storage = this.storageMap.get(cell.getParentStorageId());
        }

        if (storage == null) return;

        // Check if in same dimension
        if (storage.getDimension() != Minecraft.getMinecraft().player.dimension) {
            Minecraft.getMinecraft().player.sendMessage(
                new TextComponentTranslation("cellterminal.error.different_dimension")
            );

            return;
        }

        // Send highlight request to server
        CellTerminalNetwork.INSTANCE.sendToServer(
            new PacketHighlightBlock(storage.getPos(), storage.getDimension())
        );
    }

    protected void handleCellClick(CellInfo cell, int relX, int relY, int row, int mouseX, int mouseY) {
        int rowY = 18 + row * ROW_HEIGHT;

        // Check eject button
        if (relX >= BUTTON_EJECT_X && relX < BUTTON_EJECT_X + BUTTON_SIZE
                && relY >= rowY + 1 && relY < rowY + 1 + BUTTON_SIZE) {
            CellTerminalNetwork.INSTANCE.sendToServer(
                new PacketEjectCell(cell.getParentStorageId(), cell.getSlot())
            );

            return;
        }

        // Check inventory button
        if (relX >= BUTTON_INVENTORY_X && relX < BUTTON_INVENTORY_X + BUTTON_SIZE
                && relY >= rowY + 1 && relY < rowY + 1 + BUTTON_SIZE) {
            inventoryPopup = new PopupCellInventory(this, cell, mouseX, mouseY);

            return;
        }

        // Check partition button
        if (relX >= BUTTON_PARTITION_X && relX < BUTTON_PARTITION_X + BUTTON_SIZE
                && relY >= rowY + 1 && relY < rowY + 1 + BUTTON_SIZE) {
            partitionPopup = new PopupCellPartition(this, cell, mouseX, mouseY);
        }
    }

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        super.actionPerformed(btn);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Esc key (keyCode 1) should close modals first
        if (keyCode == 1) {
            if (inventoryPopup != null) {
                inventoryPopup = null;

                return;
            }

            if (partitionPopup != null) {
                partitionPopup = null;

                return;
            }
        }

        super.keyTyped(typedChar, keyCode);
    }

    public void postUpdate(NBTTagCompound data) {
        // Update terminal position if provided
        if (data.hasKey("terminalPos")) {
            this.terminalPos = BlockPos.fromLong(data.getLong("terminalPos"));
            this.terminalDimension = data.getInteger("terminalDim");
        }

        if (!data.hasKey("storages")) return;

        this.storageMap.clear();
        NBTTagList storageList = data.getTagList("storages", Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < storageList.tagCount(); i++) {
            NBTTagCompound storageNbt = storageList.getCompoundTagAt(i);
            StorageInfo storage = new StorageInfo(storageNbt);
            this.storageMap.put(storage.getId(), storage);
        }

        rebuildLines();
    }

    protected void rebuildLines() {
        this.lines.clear();
        this.inventoryLines.clear();
        this.partitionLines.clear();

        // Sort storages by distance to terminal (dimension first, then distance)
        List<StorageInfo> sortedStorages = new ArrayList<>(this.storageMap.values());
        sortedStorages.sort(createStorageComparator());

        for (StorageInfo storage : sortedStorages) {
            this.lines.add(storage);
            this.inventoryLines.add(storage);
            this.partitionLines.add(storage);

            // Add all cell slots (including empty) for each storage
            for (int slot = 0; slot < storage.getSlotCount(); slot++) {
                CellInfo cell = storage.getCellAtSlot(slot);

                if (cell != null) {
                    cell.setParentStorageId(storage.getId());

                    if (storage.isExpanded()) {
                        this.lines.add(cell);
                    }

                    // For inventory tab: add rows based on content count
                    int contentCount = cell.getContents().size();
                    int contentRows = Math.max(1, (contentCount + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW);
                    for (int row = 0; row < contentRows; row++) {
                        this.inventoryLines.add(new CellContentRow(cell, row * SLOTS_PER_ROW, row == 0));
                    }

                    // For partition tab: add rows based on highest non-empty slot
                    int highestSlot = getHighestNonEmptyPartitionSlot(cell);
                    int partitionRows = Math.max(1, (highestSlot + SLOTS_PER_ROW) / SLOTS_PER_ROW);
                    for (int row = 0; row < partitionRows; row++) {
                        this.partitionLines.add(new CellContentRow(cell, row * SLOTS_PER_ROW, row == 0));
                    }
                } else {
                    // Add placeholder for empty slot (only to inventory/partition tabs, not terminal)
                    EmptySlotInfo emptySlot = new EmptySlotInfo(storage.getId(), slot);
                    this.inventoryLines.add(emptySlot);
                    this.partitionLines.add(emptySlot);
                }
            }
        }

        updateScrollbarForCurrentTab();
    }

    /**
     * Get the highest slot index that contains a non-empty partition item.
     * Adds one extra row if the last slot is filled to allow for expansion.
     */
    protected int getHighestNonEmptyPartitionSlot(CellInfo cell) {
        List<ItemStack> partition = cell.getPartition();
        int highest = -1;

        for (int i = 0; i < partition.size(); i++) {
            if (!partition.get(i).isEmpty()) {
                highest = i;
            }
        }

        // If the last slot of the last visible row is filled, add an extra row
        // This allows users to add items to the next row
        if (highest >= 0) {
            int currentRows = (highest / SLOTS_PER_ROW) + 1;
            int lastSlotInLastRow = (currentRows * SLOTS_PER_ROW) - 1;

            // Check if ANY more slots are available (not just a full row)
            if (highest == lastSlotInLastRow && highest < MAX_PARTITION_SLOTS - 1) {
                // Return a value that triggers one more row (capped at max)
                highest = Math.min(highest + SLOTS_PER_ROW, MAX_PARTITION_SLOTS - 1);
            }
        }

        return highest;
    }

    protected void updateScrollbarForCurrentTab() {
        int lineCount;

        switch (currentTab) {
            case TAB_INVENTORY:
                lineCount = inventoryLines.size();
                break;
            case TAB_PARTITION:
                lineCount = partitionLines.size();
                break;
            default:
                lineCount = lines.size();
                break;
        }

        this.getScrollBar().setRange(0, Math.max(0, lineCount - ROWS_VISIBLE), 1);
    }

    protected Comparator<StorageInfo> createStorageComparator() {
        return (a, b) -> {
            // Same dimension as terminal comes first
            boolean aInDim = a.getDimension() == terminalDimension;
            boolean bInDim = b.getDimension() == terminalDimension;

            if (aInDim != bInDim) return aInDim ? -1 : 1;

            // Sort by dimension
            if (a.getDimension() != b.getDimension()) return Integer.compare(a.getDimension(), b.getDimension());

            // Sort by distance to terminal
            double distA = terminalPos.distanceSq(a.getPos());
            double distB = terminalPos.distanceSq(b.getPos());

            return Double.compare(distA, distB);
        };
    }

    // Callbacks from popups

    public void onPartitionAllClicked(CellInfo cell) {
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
            cell.getParentStorageId(),
            cell.getSlot(),
            PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS
        ));
    }

    public void onTogglePartitionItem(CellInfo cell, ItemStack stack) {
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
            cell.getParentStorageId(),
            cell.getSlot(),
            PacketPartitionAction.Action.TOGGLE_ITEM,
            stack
        ));
    }

    public void onRemovePartitionItem(CellInfo cell, int partitionSlot) {
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
            cell.getParentStorageId(),
            cell.getSlot(),
            PacketPartitionAction.Action.REMOVE_ITEM,
            partitionSlot
        ));
    }

    public void onAddPartitionItem(CellInfo cell, int partitionSlot, ItemStack stack) {
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
            cell.getParentStorageId(),
            cell.getSlot(),
            PacketPartitionAction.Action.ADD_ITEM,
            partitionSlot,
            stack
        ));
    }

    // JEI Ghost Ingredient support

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {

        // Popup takes priority - return targets for JEI drag-and-drop functionality
        // Note: We draw these targets ourselves in drawPopupJeiGhostTargets() so they appear on top of the popup
        if (partitionPopup != null && (ingredient instanceof ItemStack || ingredient instanceof FluidStack)) {
            return partitionPopup.getGhostTargets();
        }

        // Tab 3 (Partition) - expose partition slots for JEI ghost ingredients
        if (currentTab == TAB_PARTITION && (ingredient instanceof ItemStack || ingredient instanceof FluidStack)) {
            List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();

            for (PartitionSlotTarget slot : partitionSlotTargets) {
                targets.add(new IGhostIngredientHandler.Target<Object>() {
                    @Override
                    public Rectangle getArea() {
                        return new Rectangle(slot.x, slot.y, slot.width, slot.height);
                    }

                    @Override
                    public void accept(Object ing) {
                        ItemStack stack = ItemStack.EMPTY;

                        if (ing instanceof ItemStack) {
                            ItemStack itemStack = (ItemStack) ing;

                            if (slot.cell.isFluid()) {
                                // For fluid cells, try to extract fluid from the item
                                FluidStack contained = net.minecraftforge.fluids.FluidUtil.getFluidContained(itemStack);

                                if (contained == null) {
                                    Minecraft.getMinecraft().player.sendMessage(
                                        new TextComponentTranslation("cellterminal.error.fluid_cell_item")
                                    );

                                    return;
                                }

                                // Convert extracted FluidStack to ItemStack representation
                                IStorageChannel<IAEFluidStack> fluidChannel =
                                    AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
                                IAEFluidStack aeFluidStack = fluidChannel.createStack(contained);

                                if (aeFluidStack == null) return;

                                stack = aeFluidStack.asItemStackRepresentation();
                            } else {
                                stack = itemStack;
                            }
                        } else if (ing instanceof FluidStack) {
                            // For item cells, reject fluids and show error
                            if (!slot.cell.isFluid()) {
                                Minecraft.getMinecraft().player.sendMessage(
                                    new TextComponentTranslation("cellterminal.error.item_cell_fluid")
                                );

                                return;
                            }

                            // Convert FluidStack to ItemStack representation
                            FluidStack fluidStack = (FluidStack) ing;
                            appeng.api.storage.IStorageChannel<appeng.api.storage.data.IAEFluidStack> fluidChannel =
                                appeng.api.AEApi.instance().storage().getStorageChannel(appeng.api.storage.channels.IFluidStorageChannel.class);
                            appeng.api.storage.data.IAEFluidStack aeFluidStack = fluidChannel.createStack(fluidStack);

                            if (aeFluidStack == null) return;

                            stack = aeFluidStack.asItemStackRepresentation();
                        }

                        if (stack.isEmpty()) return;

                        // Replace the partition at the target slot directly
                        onAddPartitionItem(slot.cell, slot.slotIndex, stack);
                    }
                });
            }

            return targets;
        }

        return new ArrayList<>();
    }

    /**
     * Helper class to track partition slot positions for JEI ghost ingredients.
     */
    protected static class PartitionSlotTarget {
        public final CellInfo cell;
        public final int slotIndex;
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public PartitionSlotTarget(CellInfo cell, int slotIndex, int x, int y, int width, int height) {
            this.cell = cell;
            this.slotIndex = slotIndex;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
