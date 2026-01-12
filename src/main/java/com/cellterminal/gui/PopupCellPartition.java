package com.cellterminal.gui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;

import mezz.jei.api.gui.IGhostIngredientHandler;

import com.cellterminal.client.CellInfo;


/**
 * Popup overlay for editing cell partition.
 * Shows 63 slots, click to remove, JEI drag to add.
 */
public class PopupCellPartition extends Gui {

    private static final int SLOTS_PER_ROW = 9;
    private static final int MAX_ROWS = 7;
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 8;
    private static final int HEADER_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 16;
    private static final int MAX_PARTITION_SLOTS = 63;

    private final GuiScreen parent;
    private final CellInfo cell;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int slotOffsetX;

    private final List<ItemStack> editablePartition;

    // Hovered item for tooltip
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private int hoveredX = 0;
    private int hoveredY = 0;

    public PopupCellPartition(GuiScreen parent, CellInfo cell, int mouseX, int mouseY) {
        this.parent = parent;
        this.cell = cell;

        // Copy partition for editing
        this.editablePartition = new ArrayList<>(cell.getPartition());
        while (editablePartition.size() < MAX_PARTITION_SLOTS) editablePartition.add(ItemStack.EMPTY);

        // Calculate width based on title, slots, or hint, whichever is wider
        Minecraft mc = Minecraft.getMinecraft();
        String partitionSuffix = net.minecraft.client.resources.I18n.format("gui.cellterminal.popup.partition_suffix");
        String title = cell.getDisplayName() + partitionSuffix;
        String hint = net.minecraft.client.resources.I18n.format("gui.cellterminal.hint.partition");
        int titleWidth = mc.fontRenderer.getStringWidth(title) + PADDING * 2;
        int hintWidth = mc.fontRenderer.getStringWidth(hint) + PADDING * 2;
        int slotsWidth = SLOTS_PER_ROW * SLOT_SIZE + PADDING * 2;
        this.width = Math.max(Math.max(titleWidth, slotsWidth), hintWidth);
        this.height = HEADER_HEIGHT + MAX_ROWS * SLOT_SIZE + FOOTER_HEIGHT;

        // Calculate slot area offset to center slots within modal
        int slotAreaWidth = SLOTS_PER_ROW * SLOT_SIZE;
        this.slotOffsetX = (this.width - slotAreaWidth) / 2;

        // Center on screen using scaled resolution
        ScaledResolution sr = new ScaledResolution(mc);
        this.x = (sr.getScaledWidth() - this.width) / 2;
        this.y = (sr.getScaledHeight() - this.height) / 2;
    }

    public void draw(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;

        // Reset hovered state
        hoveredStack = ItemStack.EMPTY;

        // Reset GL state to known good state before drawing
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw popup background (similar to vanilla container style)
        drawRect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        drawGradientRect(x, y, x + width, y + height, 0xFFC6C6C6, 0xFFC6C6C6);

        // Draw border highlights
        drawRect(x, y, x + width, y + 1, 0xFFFFFFFF);
        drawRect(x, y, x + 1, y + height, 0xFFFFFFFF);
        drawRect(x, y + height - 1, x + width, y + height, 0xFF555555);
        drawRect(x + width - 1, y, x + width, y + height, 0xFF555555);

        // Draw title
        String partitionSuffix = net.minecraft.client.resources.I18n.format("gui.cellterminal.popup.partition_suffix");
        String title = cell.getDisplayName() + partitionSuffix;
        fr.drawString(title, x + PADDING, y + 6, 0x404040);

        // Draw partition slots
        int slotStartY = y + HEADER_HEIGHT;

        for (int i = 0; i < MAX_PARTITION_SLOTS; i++) {
            int slotX = x + slotOffsetX + (i % SLOTS_PER_ROW) * SLOT_SIZE;
            int slotY = slotStartY + (i / SLOTS_PER_ROW) * SLOT_SIZE;

            ItemStack stack = i < editablePartition.size() ? editablePartition.get(i) : ItemStack.EMPTY;

            // Draw slot background
            int slotBgColor = 0xFF8B8B8B;
            boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE - 1
                && mouseY >= slotY && mouseY < slotY + SLOT_SIZE - 1;
            if (hovered && !stack.isEmpty()) slotBgColor = 0xFF996666;

            drawRect(slotX, slotY, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, slotBgColor);

            // Draw slot border (3D effect)
            drawRect(slotX, slotY, slotX + SLOT_SIZE - 1, slotY + 1, 0xFF373737);
            drawRect(slotX, slotY, slotX + 1, slotY + SLOT_SIZE - 1, 0xFF373737);
            drawRect(slotX, slotY + SLOT_SIZE - 2, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, 0xFFFFFFFF);
            drawRect(slotX + SLOT_SIZE - 2, slotY, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, 0xFFFFFFFF);

            // Draw item
            if (!stack.isEmpty()) {
                GlStateManager.enableDepth();
                RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, slotX, slotY);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableDepth();
                GlStateManager.disableLighting();

                // Track hovered item for tooltip
                if (hovered) {
                    hoveredStack = stack;
                    hoveredX = mouseX;
                    hoveredY = mouseY;
                }
            }
        }

        // Draw hint at bottom
        String hint = net.minecraft.client.resources.I18n.format("gui.cellterminal.hint.partition");
        int hintWidth = fr.getStringWidth(hint);
        fr.drawString(hint, x + (width - hintWidth) / 2, y + height - FOOTER_HEIGHT + 2, 0x606060);

        // Reset state for subsequent rendering
        GlStateManager.enableDepth();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Draw tooltip for hovered item. Must be called after draw() in a separate pass.
     */
    public void drawTooltip(int mouseX, int mouseY) {
        if (!hoveredStack.isEmpty() && parent instanceof GuiScreen) {
            ((GuiScreen) parent).drawHoveringText(
                parent.getItemToolTip(hoveredStack),
                hoveredX,
                hoveredY
            );
        }
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!isInsidePopup(mouseX, mouseY)) return false;

        // Check slot click to remove
        int slotStartY = y + HEADER_HEIGHT;
        int relX = mouseX - x - slotOffsetX;
        int relY = mouseY - slotStartY;

        if (relX >= 0 && relX < SLOTS_PER_ROW * SLOT_SIZE && relY >= 0 && relY < MAX_ROWS * SLOT_SIZE) {
            int slotCol = relX / SLOT_SIZE;
            int slotRow = relY / SLOT_SIZE;
            int slotIndex = slotRow * SLOTS_PER_ROW + slotCol;

            if (slotIndex < MAX_PARTITION_SLOTS && slotIndex < editablePartition.size()) {
                ItemStack removed = editablePartition.get(slotIndex);
                if (!removed.isEmpty()) {
                    editablePartition.set(slotIndex, ItemStack.EMPTY);

                    if (parent instanceof GuiCellTerminalBase) {
                        ((GuiCellTerminalBase) parent).onRemovePartitionItem(cell, slotIndex);
                    }
                }

                return true;
            }
        }

        return true;
    }

    /**
     * Convert any JEI ingredient to an ItemStack for use with AE2 cells.
     * Handles ItemStack, FluidStack, EnchantmentData (JEI's hack for enchanted books),
     * and any future/unknown ingredient types.
     *
     * @param ingredient The JEI ingredient to convert
     * @return The converted ItemStack, or ItemStack.EMPTY if conversion failed or was rejected
     */
    private ItemStack convertJeiIngredientToItemStack(Object ingredient) {
        // Direct ItemStack - most common case
        if (ingredient instanceof ItemStack) {
            ItemStack itemStack = (ItemStack) ingredient;

            if (cell.isFluid()) {
                // For fluid cells, try to extract fluid from the item (e.g., bucket)
                FluidStack contained = FluidUtil.getFluidContained(itemStack);

                if (contained == null) {
                    Minecraft.getMinecraft().player.sendMessage(
                        new TextComponentTranslation("cellterminal.error.fluid_cell_item")
                    );

                    return ItemStack.EMPTY;
                }

                IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
                IAEFluidStack aeFluidStack = fluidChannel.createStack(contained);

                if (aeFluidStack == null) return ItemStack.EMPTY;

                return aeFluidStack.asItemStackRepresentation();
            }

            return itemStack;
        }

        // FluidStack - from JEI fluid entries
        if (ingredient instanceof FluidStack) {
            if (!cell.isFluid()) {
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentTranslation("cellterminal.error.item_cell_fluid")
                );

                return ItemStack.EMPTY;
            }

            FluidStack fluidStack = (FluidStack) ingredient;
            IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            IAEFluidStack aeFluidStack = fluidChannel.createStack(fluidStack);

            if (aeFluidStack == null) return ItemStack.EMPTY;

            return aeFluidStack.asItemStackRepresentation();
        }

        // EnchantmentData - JEI's deprecated hack for enchanted books (removed in 1.13+)
        if (ingredient instanceof EnchantmentData) {
            if (cell.isFluid()) {
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentTranslation("cellterminal.error.fluid_cell_item")
                );

                return ItemStack.EMPTY;
            }

            EnchantmentData enchantData = (EnchantmentData) ingredient;

            return ItemEnchantedBook.getEnchantedItemStack(enchantData);
        }

        // Unknown ingredient type - reject silently (popup doesn't need logging)
        return ItemStack.EMPTY;
    }

    /**
     * Handle JEI ghost ingredient drop.
     * FIXME: The green slots do not appear when dragging from JEI into the popup.
     * FIXME: The green slots are not cleared when the popup is closed.
     * FIXME: The green line is not rendered when dragging from bookmarks.
     * FIXME: The item is rendered behind the popup when dragging from bookmarks.
     */
    public boolean handleGhostDrop(int slotIndex, Object ingredient) {
        if (slotIndex < 0 || slotIndex >= MAX_PARTITION_SLOTS) return false;

        ItemStack stack = convertJeiIngredientToItemStack(ingredient);

        if (stack.isEmpty()) return false;

        // Find first empty slot if dropping on occupied slot
        int targetSlot = slotIndex;
        if (!editablePartition.get(slotIndex).isEmpty()) {
            targetSlot = findEmptySlot();
            if (targetSlot == -1) return false;
        }

        editablePartition.set(targetSlot, stack.copy());

        if (parent instanceof GuiCellTerminalBase) {
            ((GuiCellTerminalBase) parent).onAddPartitionItem(cell, targetSlot, stack);
        }

        return true;
    }

    // OLD EXPLICIT TYPE HANDLING - Uncomment this and remove convertJeiIngredientToItemStack
    // if you need to revert to the previous behavior due to issues with unknown ingredient types.
    /*
    public boolean handleGhostDrop(int slotIndex, Object ingredient) {
        if (slotIndex < 0 || slotIndex >= MAX_PARTITION_SLOTS) return false;

        ItemStack stack;

        if (ingredient instanceof ItemStack) {
            ItemStack itemStack = (ItemStack) ingredient;
            if (cell.isFluid()) {
                FluidStack contained = FluidUtil.getFluidContained(itemStack);
                if (contained == null) {
                    Minecraft.getMinecraft().player.sendMessage(new TextComponentTranslation("cellterminal.error.fluid_cell_item"));
                    return false;
                }
                IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
                IAEFluidStack aeFluidStack = fluidChannel.createStack(contained);
                if (aeFluidStack == null) return false;
                stack = aeFluidStack.asItemStackRepresentation();
            } else {
                stack = itemStack;
            }
        } else if (ingredient instanceof FluidStack) {
            if (!cell.isFluid()) {
                Minecraft.getMinecraft().player.sendMessage(new TextComponentTranslation("cellterminal.error.item_cell_fluid"));
                return false;
            }
            FluidStack fluidStack = (FluidStack) ingredient;
            IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            IAEFluidStack aeFluidStack = fluidChannel.createStack(fluidStack);
            if (aeFluidStack == null) return false;
            stack = aeFluidStack.asItemStackRepresentation();
        } else if (ingredient instanceof EnchantmentData) {
            if (cell.isFluid()) {
                Minecraft.getMinecraft().player.sendMessage(new TextComponentTranslation("cellterminal.error.fluid_cell_item"));
                return false;
            }
            EnchantmentData enchantData = (EnchantmentData) ingredient;
            stack = ItemEnchantedBook.getEnchantedItemStack(enchantData);
        } else {
            return false;
        }

        if (stack.isEmpty()) return false;

        int targetSlot = slotIndex;
        if (!editablePartition.get(slotIndex).isEmpty()) {
            targetSlot = findEmptySlot();
            if (targetSlot == -1) return false;
        }

        editablePartition.set(targetSlot, stack.copy());

        if (parent instanceof GuiCellTerminalBase) {
            ((GuiCellTerminalBase) parent).onAddPartitionItem(cell, targetSlot, stack);
        }

        return true;
    }
    */

    private int findEmptySlot() {
        for (int i = 0; i < editablePartition.size(); i++) {
            if (editablePartition.get(i).isEmpty()) return i;
        }

        return -1;
    }

    public boolean isInsidePopup(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * Get JEI ghost ingredient targets for this popup.
     * The parent GUI will wrap these to handle clearing the drag state.
     */
    public List<IGhostIngredientHandler.Target<?>> getGhostTargets() {
        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        int slotStartY = y + HEADER_HEIGHT;

        for (int i = 0; i < MAX_PARTITION_SLOTS; i++) {
            final int slotIndex = i;
            int slotX = x + slotOffsetX + (i % SLOTS_PER_ROW) * SLOT_SIZE;
            int slotY = slotStartY + (i / SLOTS_PER_ROW) * SLOT_SIZE;

            Rectangle area = new Rectangle(slotX, slotY, SLOT_SIZE - 1, SLOT_SIZE - 1);

            targets.add(new IGhostIngredientHandler.Target<Object>() {
                @Override
                public Rectangle getArea() {
                    return area;
                }

                @Override
                public void accept(Object ingredient) {
                    // Handle both ItemStack and FluidStack
                    handleGhostDrop(slotIndex, ingredient);
                }
            });
        }

        return targets;
    }

    public int getSlotAtPosition(int mouseX, int mouseY) {
        int slotStartY = y + HEADER_HEIGHT;
        int relX = mouseX - x - slotOffsetX;
        int relY = mouseY - slotStartY;

        if (relX >= 0 && relX < SLOTS_PER_ROW * SLOT_SIZE && relY >= 0 && relY < MAX_ROWS * SLOT_SIZE) {
            int slotCol = relX / SLOT_SIZE;
            int slotRow = relY / SLOT_SIZE;

            return slotRow * SLOTS_PER_ROW + slotCol;
        }

        return -1;
    }

    public CellInfo getCell() {
        return cell;
    }

    public List<ItemStack> getEditablePartition() {
        return editablePartition;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
