package com.diskterminal.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;

import mezz.jei.api.gui.IGhostIngredientHandler;

import com.diskterminal.client.DiskInfo;
import com.diskterminal.client.StorageInfo;
import com.diskterminal.network.DiskTerminalNetwork;
import com.diskterminal.network.PacketEjectDisk;
import com.diskterminal.network.PacketHighlightBlock;
import com.diskterminal.network.PacketInsertCell;
import com.diskterminal.network.PacketPartitionAction;


/**
 * Base GUI for Disk Terminal variants.
 * Contains shared functionality for displaying storage drives/chests with their disks.
 */
public abstract class GuiDiskTerminalBase extends AEBaseGui implements IJEIGhostIngredients {

    protected static final int ROW_HEIGHT = 18;
    protected static final int GUI_INDENT = 22;
    protected static final int DISK_INDENT = GUI_INDENT + 12;
    protected static final int ROWS_VISIBLE = 8;

    // Button dimensions
    protected static final int BUTTON_SIZE = 14;
    protected static final int BUTTON_EJECT_X = 135;
    protected static final int BUTTON_INVENTORY_X = 150;
    protected static final int BUTTON_PARTITION_X = 165;

    protected final Map<Long, StorageInfo> storageMap = new LinkedHashMap<>();
    protected final List<Object> lines = new ArrayList<>();

    // Popup states
    protected PopupDiskInventory inventoryPopup = null;
    protected PopupDiskPartition partitionPopup = null;
    protected DiskInfo hoveredDisk = null;
    protected int hoverType = 0; // 0=none, 1=inventory, 2=partition, 3=eject

    // Hover tracking for background highlight
    protected int hoveredLineIndex = -1;

    // Double-click tracking
    protected long lastClickTime = 0;
    protected int lastClickedLineIndex = -1;

    // Terminal position for sorting (set by container)
    protected BlockPos terminalPos = BlockPos.ORIGIN;
    protected int terminalDimension = 0;

    public GuiDiskTerminalBase(Container container) {
        super(container);

        this.xSize = 208;
        this.ySize = 222;

        GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
    }

    protected abstract String getGuiTitle();

    @Override
    public void initGui() {
        super.initGui();
        this.getScrollBar().setTop(18).setLeft(189).setHeight(ROWS_VISIBLE * ROW_HEIGHT - 2);
        this.repositionSlots();
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

        // Draw popups on top
        if (inventoryPopup != null) {
            inventoryPopup.draw(mouseX, mouseY);
            inventoryPopup.drawTooltip(mouseX, mouseY);
        }

        if (partitionPopup != null) {
            partitionPopup.draw(mouseX, mouseY);
            partitionPopup.drawTooltip(mouseX, mouseY);
        }

        // Draw hover preview if hovering over button
        if (hoveredDisk != null && inventoryPopup == null && partitionPopup == null) {
            int previewX = mouseX + 10;
            int previewY = mouseY + 10;

            if (hoverType == 1) {
                PopupDiskInventory preview = new PopupDiskInventory(this, hoveredDisk, previewX, previewY);
                preview.draw(mouseX, mouseY);
            } else if (hoverType == 2) {
                PopupDiskPartition preview = new PopupDiskPartition(this, hoveredDisk, previewX, previewY);
                preview.draw(mouseX, mouseY);
            } else if (hoverType == 3) {
                List<String> tooltip = Collections.singletonList("Eject disk");
                this.drawHoveringText(tooltip, mouseX, mouseY);
            }
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(getGuiTitle(), 22, 6, 0x404040);
        this.fontRenderer.drawString("Inventory", 22, this.ySize - 58 + 3, 0x404040);

        hoveredDisk = null;
        hoverType = 0;
        hoveredLineIndex = -1;

        int y = 18;
        final int currentScroll = this.getScrollBar().getCurrentScroll();
        int relMouseX = mouseX - offsetX;
        int relMouseY = mouseY - offsetY;

        for (int i = 0; i < ROWS_VISIBLE && currentScroll + i < lines.size(); i++) {
            Object line = lines.get(currentScroll + i);
            int lineIndex = currentScroll + i;

            // Check if mouse is hovering over this line
            boolean isHovered = relMouseX >= 4 && relMouseX < 185
                && relMouseY >= y && relMouseY < y + ROW_HEIGHT;

            // Draw hover background for disk lines
            if (isHovered && line instanceof DiskInfo) {
                hoveredLineIndex = lineIndex;
                drawRect(GUI_INDENT, y - 1, 180, y + ROW_HEIGHT - 1, 0x50CCCCCC);
            }

            // Draw separator line above storage entries (except first one)
            if (line instanceof StorageInfo && i > 0) drawRect(GUI_INDENT, y - 1, 180, y, 0xFF606060);

            if (line instanceof StorageInfo) {
                drawStorageLine((StorageInfo) line, y);
            } else if (line instanceof DiskInfo) {
                drawDiskLine((DiskInfo) line, y, relMouseX, relMouseY);
            }

            y += ROW_HEIGHT;
        }
    }

    protected void drawStorageLine(StorageInfo storage, int y) {
        // Draw expand/collapse indicator on the right
        String expandIcon = storage.isExpanded() ? "[-]" : "[+]";
        this.fontRenderer.drawString(expandIcon, 165, y + 6, 0x606060);

        // Draw block icon
        if (!storage.getBlockItem().isEmpty()) {
            RenderHelper.enableGUIStandardItemLighting();
            this.itemRender.renderItemIntoGUI(storage.getBlockItem(), GUI_INDENT, y);
            RenderHelper.disableStandardItemLighting();
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

    protected void drawDiskLine(DiskInfo disk, int y, int mouseX, int mouseY) {
        // Draw tree line to show hierarchy (extends upward to connect to previous row)
        int lineX = GUI_INDENT + 7;
        drawRect(lineX, y - ROW_HEIGHT + 9, lineX + 1, y + 9, 0xFF808080);
        drawRect(lineX, y + 8, lineX + 10, y + 9, 0xFF808080);

        // Draw disk icon with indent
        if (!disk.getCellItem().isEmpty()) {
            RenderHelper.enableGUIStandardItemLighting();
            this.itemRender.renderItemIntoGUI(disk.getCellItem(), DISK_INDENT, y);
            RenderHelper.disableStandardItemLighting();
        }

        // Draw disk name
        String name = disk.getDisplayName();
        int decorLength = getDecorationLength(name);
        if (name.length() - decorLength > 16) name = name.substring(0, 14 + decorLength) + "...";
        this.fontRenderer.drawString(name, DISK_INDENT + 18, y + 1, 0x404040);

        // Draw usage bar
        int barX = DISK_INDENT + 18;
        int barY = y + 10;
        int barWidth = 80;
        int barHeight = 4;

        drawRect(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);
        int filledWidth = (int) (barWidth * disk.getByteUsagePercent());
        int fillColor = getUsageColor(disk.getByteUsagePercent());
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
            hoveredDisk = disk;
            hoverType = 3;
        } else if (invHovered) {
            hoveredDisk = disk;
            hoverType = 1;
        } else if (partHovered) {
            hoveredDisk = disk;
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
        this.bindTexture("guis/newinterfaceterminal.png");

        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, 18);

        for (int i = 0; i < ROWS_VISIBLE; i++) {
            this.drawTexturedModalRect(offsetX, offsetY + 18 + i * ROW_HEIGHT, 0, 52, this.xSize, ROW_HEIGHT);
        }

        // TODO: we need a top groove, like the rest of the sides in the rect
        this.drawTexturedModalRect(offsetX, offsetY + 18 + ROWS_VISIBLE * ROW_HEIGHT, 0, 158, this.xSize, 99);
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

        super.mouseClicked(mouseX, mouseY, mouseButton);

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
            } else if (line instanceof DiskInfo) {
                storageId = ((DiskInfo) line).getParentStorageId();
            }

            if (storageId >= 0) {
                DiskTerminalNetwork.INSTANCE.sendToServer(
                    new PacketInsertCell(storageId, -1)
                );

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

        if (line instanceof DiskInfo) {
            handleDiskClick((DiskInfo) line, relX, relY, row, mouseX, mouseY);
        }
    }

    protected void handleDoubleClick(Object line) {
        StorageInfo storage = null;

        if (line instanceof StorageInfo) {
            storage = (StorageInfo) line;
        } else if (line instanceof DiskInfo) {
            DiskInfo disk = (DiskInfo) line;
            storage = this.storageMap.get(disk.getParentStorageId());
        }

        if (storage == null) return;

        // Check if in same dimension
        if (storage.getDimension() != Minecraft.getMinecraft().player.dimension) {
            Minecraft.getMinecraft().player.sendMessage(
                new TextComponentTranslation("diskterminal.error.different_dimension")
            );

            return;
        }

        // Send highlight request to server
        DiskTerminalNetwork.INSTANCE.sendToServer(
            new PacketHighlightBlock(storage.getPos(), storage.getDimension())
        );
    }

    protected void handleDiskClick(DiskInfo disk, int relX, int relY, int row, int mouseX, int mouseY) {
        int rowY = 18 + row * ROW_HEIGHT;

        // Check eject button
        if (relX >= BUTTON_EJECT_X && relX < BUTTON_EJECT_X + BUTTON_SIZE
                && relY >= rowY + 1 && relY < rowY + 1 + BUTTON_SIZE) {
            DiskTerminalNetwork.INSTANCE.sendToServer(
                new PacketEjectDisk(disk.getParentStorageId(), disk.getSlot())
            );

            return;
        }

        // Check inventory button
        if (relX >= BUTTON_INVENTORY_X && relX < BUTTON_INVENTORY_X + BUTTON_SIZE
                && relY >= rowY + 1 && relY < rowY + 1 + BUTTON_SIZE) {
            inventoryPopup = new PopupDiskInventory(this, disk, mouseX, mouseY);

            return;
        }

        // Check partition button
        if (relX >= BUTTON_PARTITION_X && relX < BUTTON_PARTITION_X + BUTTON_SIZE
                && relY >= rowY + 1 && relY < rowY + 1 + BUTTON_SIZE) {
            partitionPopup = new PopupDiskPartition(this, disk, mouseX, mouseY);
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

        // Sort storages by distance to terminal (dimension first, then distance)
        List<StorageInfo> sortedStorages = new ArrayList<>(this.storageMap.values());
        sortedStorages.sort(createStorageComparator());

        for (StorageInfo storage : sortedStorages) {
            this.lines.add(storage);

            if (storage.isExpanded()) {
                for (DiskInfo disk : storage.getDisks()) {
                    disk.setParentStorageId(storage.getId());
                    this.lines.add(disk);
                }
            }
        }

        this.getScrollBar().setRange(0, Math.max(0, this.lines.size() - ROWS_VISIBLE), 1);
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

    public void onPartitionAllClicked(DiskInfo disk) {
        DiskTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
            disk.getParentStorageId(),
            disk.getSlot(),
            PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS
        ));
    }

    public void onTogglePartitionItem(DiskInfo disk, ItemStack stack) {
        DiskTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
            disk.getParentStorageId(),
            disk.getSlot(),
            PacketPartitionAction.Action.TOGGLE_ITEM,
            stack
        ));
    }

    public void onRemovePartitionItem(DiskInfo disk, int partitionSlot) {
        DiskTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
            disk.getParentStorageId(),
            disk.getSlot(),
            PacketPartitionAction.Action.REMOVE_ITEM,
            partitionSlot
        ));
    }

    public void onAddPartitionItem(DiskInfo disk, int partitionSlot, ItemStack stack) {
        DiskTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
            disk.getParentStorageId(),
            disk.getSlot(),
            PacketPartitionAction.Action.ADD_ITEM,
            partitionSlot,
            stack
        ));
    }

    // JEI Ghost Ingredient support

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        if (partitionPopup != null && (ingredient instanceof ItemStack || ingredient instanceof FluidStack)) {
            return partitionPopup.getGhostTargets();
        }

        return new ArrayList<>();
    }
}
