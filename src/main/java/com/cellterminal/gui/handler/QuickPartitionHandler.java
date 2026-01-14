package com.cellterminal.gui.handler;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;

import mezz.jei.api.IBookmarkOverlay;
import mezz.jei.api.IIngredientListOverlay;
import mezz.jei.api.IJeiRuntime;

import com.cellterminal.CellTerminal;
import com.cellterminal.client.CellContentRow;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.StorageInfo;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketPartitionAction;

import javax.annotation.Nullable;


/**
 * Handles quick partition keybind actions.
 * Finds the hovered item, locates a suitable cell, and partitions it.
 */
public class QuickPartitionHandler {

    public enum PartitionType {
        AUTO,
        ITEM,
        FLUID,
        ESSENTIA
    }

    /**
     * Result of a quick partition attempt.
     */
    public static class QuickPartitionResult {
        public final boolean success;
        public final String message;
        public final int scrollToLine;

        public QuickPartitionResult(boolean success, String message, int scrollToLine) {
            this.success = success;
            this.message = message;
            this.scrollToLine = scrollToLine;
        }

        public static QuickPartitionResult error(String messageKey) {
            return new QuickPartitionResult(false, I18n.format(messageKey), -1);
        }

        public static QuickPartitionResult error(String messageKey, Object... args) {
            return new QuickPartitionResult(false, I18n.format(messageKey, args), -1);
        }

        public static QuickPartitionResult success(String message, int scrollToLine) {
            return new QuickPartitionResult(true, message, scrollToLine);
        }
    }

    private static IJeiRuntime jeiRuntime = null;

    /**
     * Set the JEI runtime for ingredient lookup.
     * This should be called when JEI is loaded.
     */
    public static void setJeiRuntime(IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    /**
     * Attempt to quick partition based on the hovered item.
     *
     * @param type The type of cell to target
     * @param partitionLines The current partition lines
     * @param storageMap The storage map
     * @return Result with success/failure message and scroll position
     */
    public static QuickPartitionResult attemptQuickPartition(
            PartitionType type,
            List<Object> partitionLines,
            Map<Long, StorageInfo> storageMap) {

        // Check for Thaumic Energistics if trying to partition to essentia cell
        if (type == PartitionType.ESSENTIA && !ThaumicEnergisticsIntegration.isModLoaded()) {
            return QuickPartitionResult.error("cellterminal.quick_partition.essentia_unavailable");
        }

        // Get the ingredient under cursor from various sources (preserving original type)
        HoveredIngredient hoveredIngredient = getHoveredIngredient();
        if (hoveredIngredient == null || hoveredIngredient.stack.isEmpty()) {
            return QuickPartitionResult.error("cellterminal.quick_partition.no_item");
        }

        // Determine target cell type
        PartitionType targetType = type;
        if (type == PartitionType.AUTO) targetType = hoveredIngredient.inferredType;

        // Convert stack for the target cell type
        ItemStack partitionStack = convertStackForCellType(hoveredIngredient.stack, targetType, hoveredIngredient.originalIngredient);
        if (partitionStack.isEmpty()) {
            // Conversion failed - show type-specific error message
            switch (targetType) {
                case FLUID:
                    return QuickPartitionResult.error("cellterminal.error.fluid_cell_item");
                case ESSENTIA:
                    return QuickPartitionResult.error("cellterminal.error.essentia_cell_item");
                default:
                    return QuickPartitionResult.error("cellterminal.quick_partition.no_item");
            }
        }

        // Find first matching cell without partition
        CellSearchResult searchResult = findFirstCellWithoutPartition(targetType, partitionLines, storageMap);
        if (searchResult == null) {
            switch (targetType) {
                case ITEM:
                    return QuickPartitionResult.error("cellterminal.quick_partition.no_cell_item");
                case FLUID:
                    return QuickPartitionResult.error("cellterminal.quick_partition.no_cell_fluid");
                case ESSENTIA:
                    return QuickPartitionResult.error("cellterminal.quick_partition.no_cell_essentia");
                default:
                    return QuickPartitionResult.error("cellterminal.quick_partition.no_cell_auto");
            }
        }

        // Send partition packet
        CellTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
            searchResult.cell.getParentStorageId(),
            searchResult.cell.getSlot(),
            PacketPartitionAction.Action.ADD_ITEM,
            0,
            partitionStack
        ));

        String cellName = searchResult.cell.getCellItem().getDisplayName();
        String itemName = partitionStack.getDisplayName();
        String successMessage = I18n.format("cellterminal.quick_partition.success", itemName, cellName);

        return QuickPartitionResult.success(successMessage, searchResult.lineIndex);
    }

    /**
     * Holds information about a hovered ingredient, preserving its original type.
     */
    private static class HoveredIngredient {
        final ItemStack stack;
        final Object originalIngredient;
        final PartitionType inferredType;

        HoveredIngredient(ItemStack stack, Object originalIngredient, PartitionType inferredType) {
            this.stack = stack;
            this.originalIngredient = originalIngredient;
            this.inferredType = inferredType;
        }
    }

    /**
     * Get the ingredient currently under the mouse cursor.
     * Checks: player inventory slots, JEI ingredient list, JEI bookmarks.
     * Preserves original ingredient type for proper cell type inference.
     */
    @Nullable
    private static HoveredIngredient getHoveredIngredient() {
        Minecraft mc = Minecraft.getMinecraft();

        // Check player inventory slots first (always item type)
        if (mc.currentScreen instanceof GuiContainer) {
            GuiContainer guiContainer = (GuiContainer) mc.currentScreen;
            Slot hoveredSlot = guiContainer.getSlotUnderMouse();

            if (hoveredSlot != null && hoveredSlot.getHasStack()) {
                ItemStack stack = hoveredSlot.getStack().copy();
                return new HoveredIngredient(stack, stack, PartitionType.ITEM);
            }
        }

        // Check JEI if available
        if (Loader.isModLoaded("jei")) return getJeiIngredientWithType();

        return null;
    }

    @Optional.Method(modid = "jei")
    @Nullable
    private static HoveredIngredient getJeiIngredientWithType() {
        if (jeiRuntime == null) return null;

        // Check ingredient list
        IIngredientListOverlay ingredientList = jeiRuntime.getIngredientListOverlay();
        Object ingredient = ingredientList.getIngredientUnderMouse();

        if (ingredient != null) return convertJeiIngredientWithType(ingredient);

        // Check bookmarks
        IBookmarkOverlay bookmarks = jeiRuntime.getBookmarkOverlay();
        ingredient = bookmarks.getIngredientUnderMouse();

        if (ingredient != null) return convertJeiIngredientWithType(ingredient);

        return null;
    }

    @Nullable
    private static HoveredIngredient convertJeiIngredientWithType(Object ingredient) {
        if (ingredient instanceof ItemStack) {
            ItemStack stack = ((ItemStack) ingredient).copy();
            return new HoveredIngredient(stack, ingredient, PartitionType.ITEM);
        }

        if (ingredient instanceof EnchantmentData) {
            ItemStack stack = ItemEnchantedBook.getEnchantedItemStack((EnchantmentData) ingredient);
            return new HoveredIngredient(stack, ingredient, PartitionType.ITEM);
        }

        if (ingredient instanceof FluidStack) {
            FluidStack fluidStack = (FluidStack) ingredient;
            IStorageChannel<IAEFluidStack> fluidChannel =
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            IAEFluidStack aeFluidStack = fluidChannel.createStack(fluidStack);

            if (aeFluidStack != null) {
                return new HoveredIngredient(aeFluidStack.asItemStackRepresentation(), ingredient, PartitionType.FLUID);
            }
        }

        // Check for essentia (Thaumic Energistics)
        if (ThaumicEnergisticsIntegration.isModLoaded()) {
            HoveredIngredient essentiaResult = tryConvertEssentiaIngredient(ingredient);
            if (essentiaResult != null) return essentiaResult;
        }

        CellTerminal.LOGGER.warn("Unknown JEI ingredient type: {}. Report to the author!", ingredient.getClass().getName());
        return null;
    }

    @Nullable
    private static HoveredIngredient tryConvertEssentiaIngredient(Object ingredient) {
        // Try to detect essentia stacks from Thaumic Energistics
        ItemStack essentiaRep = ThaumicEnergisticsIntegration.tryConvertJeiIngredientToEssentia(ingredient);

        if (!essentiaRep.isEmpty()) return new HoveredIngredient(essentiaRep, ingredient, PartitionType.ESSENTIA);

        return null;
    }

    /**
     * Convert an ItemStack to the appropriate format for a cell type.
     * Returns EMPTY if conversion is not possible (e.g., item for fluid cell).
     */
    private static ItemStack convertStackForCellType(ItemStack stack, PartitionType type, @Nullable Object originalIngredient) {
        switch (type) {
            case FLUID:
                // If original ingredient was already a FluidStack, the stack is already converted
                if (originalIngredient instanceof FluidStack) return stack;

                // Try to extract fluid from container
                FluidStack contained = FluidUtil.getFluidContained(stack);

                if (contained != null) {
                    IStorageChannel<IAEFluidStack> fluidChannel =
                        AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
                    IAEFluidStack aeFluidStack = fluidChannel.createStack(contained);

                    if (aeFluidStack != null) return aeFluidStack.asItemStackRepresentation();
                }

                // Item cannot be converted to fluid - return empty
                return ItemStack.EMPTY;

            case ESSENTIA:
                // If original was already an essentia ingredient, the stack is already converted
                if (ThaumicEnergisticsIntegration.isEssentiaIngredient(originalIngredient)) return stack;

                // Try to extract essentia from container
                ItemStack essentiaRep = ThaumicEnergisticsIntegration.tryConvertEssentiaContainerToAspect(stack);

                if (!essentiaRep.isEmpty()) return essentiaRep;

                // Item cannot be converted to essentia - return empty
                return ItemStack.EMPTY;

            case ITEM:
                return stack.copy();

            case AUTO:
                // AUTO should have been resolved before reaching here
                CellTerminal.LOGGER.error("Unhandled AUTO partition type in convertStackForCellType. This should not happen - report to the author!");
                return stack.copy();

            default:
                CellTerminal.LOGGER.error("Unhandled partition type: {}. This should not happen - report to the author!", type);
                return stack.copy();
        }
    }

    private static class CellSearchResult {
        final CellInfo cell;
        final int lineIndex;

        CellSearchResult(CellInfo cell, int lineIndex) {
            this.cell = cell;
            this.lineIndex = lineIndex;
        }
    }

    /**
     * Find the first cell of the specified type that has no partition configured.
     */
    private static CellSearchResult findFirstCellWithoutPartition(
            PartitionType type,
            List<Object> partitionLines,
            java.util.Map<Long, StorageInfo> storageMap) {

        for (int i = 0; i < partitionLines.size(); i++) {
            Object line = partitionLines.get(i);
            if (!(line instanceof CellContentRow)) continue;

            CellContentRow row = (CellContentRow) line;
            if (!row.isFirstRow()) continue;

            // Check cell type matches
            CellInfo cell = row.getCell();
            if (!matchesCellType(cell, type)) continue;

            // Check if partition is empty
            if (!hasEmptyPartition(cell)) continue;

            return new CellSearchResult(cell, i);
        }

        return null;
    }

    private static boolean matchesCellType(CellInfo cell, PartitionType type) {
        switch (type) {
            case ITEM:
                return !cell.isFluid() && !cell.isEssentia();
            case FLUID:
                return cell.isFluid();
            case ESSENTIA:
                return cell.isEssentia();
            case AUTO:
                return true;
            default:
                return false;
        }
    }

    private static boolean hasEmptyPartition(CellInfo cell) {
        List<ItemStack> partition = cell.getPartition();

        if (partition.isEmpty()) return true;

        for (ItemStack stack : partition) {
            if (!stack.isEmpty()) return false;
        }

        return true;
    }
}
