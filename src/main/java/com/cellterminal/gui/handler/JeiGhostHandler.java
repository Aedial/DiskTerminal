package com.cellterminal.gui.handler;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
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

import com.cellterminal.CellTerminal;
import com.cellterminal.client.CellInfo;
import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.gui.PopupCellPartition;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;


/**
 * Handles JEI Ghost Ingredient support for Cell Terminal GUI.
 */
public class JeiGhostHandler {

    /**
     * Target for a partition slot that can receive JEI ghost ingredients.
     */
    public static class PartitionSlotTarget {
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

    public interface PartitionCallback {
        void onAddPartitionItem(CellInfo cell, int slotIndex, ItemStack stack);
    }

    /**
     * Target for a storage bus partition slot that can receive JEI ghost ingredients.
     */
    public static class StorageBusPartitionSlotTarget {
        public final StorageBusInfo storageBus;
        public final int slotIndex;
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public StorageBusPartitionSlotTarget(StorageBusInfo storageBus, int slotIndex, int x, int y, int width, int height) {
            this.storageBus = storageBus;
            this.slotIndex = slotIndex;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public interface StorageBusPartitionCallback {
        void onAddStorageBusPartitionItem(StorageBusInfo storageBus, int slotIndex, ItemStack stack);
    }

    /**
     * Convert any JEI ingredient to an ItemStack for use with AE2 cells.
     */
    public static ItemStack convertJeiIngredientToItemStack(Object ingredient, boolean isFluidCell, boolean isEssentiaCell) {
        if (ingredient instanceof ItemStack) {
            ItemStack itemStack = (ItemStack) ingredient;

            // For essentia cells, try to extract essentia from containers (phials, jars, etc.)
            if (isEssentiaCell) {
                ItemStack essentiaRep = ThaumicEnergisticsIntegration.tryConvertEssentiaContainerToAspect(itemStack);

                if (!essentiaRep.isEmpty()) return essentiaRep;

                // If it's not an essentia container, reject it
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentTranslation("cellterminal.error.essentia_cell_item")
                );

                return ItemStack.EMPTY;
            }

            if (isFluidCell) {
                FluidStack contained = FluidUtil.getFluidContained(itemStack);

                if (contained == null) {
                    Minecraft.getMinecraft().player.sendMessage(
                        new TextComponentTranslation("cellterminal.error.fluid_cell_item")
                    );

                    return ItemStack.EMPTY;
                }

                IStorageChannel<IAEFluidStack> fluidChannel =
                    AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
                IAEFluidStack aeFluidStack = fluidChannel.createStack(contained);

                if (aeFluidStack == null) return ItemStack.EMPTY;

                return aeFluidStack.asItemStackRepresentation();
            }

            return itemStack;
        }

        if (ingredient instanceof FluidStack) {
            if (isEssentiaCell) {
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentTranslation("cellterminal.error.essentia_cell_fluid")
                );

                return ItemStack.EMPTY;
            }

            if (!isFluidCell) {
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentTranslation("cellterminal.error.item_cell_fluid")
                );

                return ItemStack.EMPTY;
            }

            FluidStack fluidStack = (FluidStack) ingredient;
            IStorageChannel<IAEFluidStack> fluidChannel =
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            IAEFluidStack aeFluidStack = fluidChannel.createStack(fluidStack);

            if (aeFluidStack == null) return ItemStack.EMPTY;

            return aeFluidStack.asItemStackRepresentation();
        }

        if (ingredient instanceof EnchantmentData) {
            if (isFluidCell || isEssentiaCell) {
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentTranslation(isFluidCell ? "cellterminal.error.fluid_cell_item" : "cellterminal.error.essentia_cell_item")
                );

                return ItemStack.EMPTY;
            }

            EnchantmentData enchantData = (EnchantmentData) ingredient;

            return ItemEnchantedBook.getEnchantedItemStack(enchantData);
        }

        CellTerminal.LOGGER.warn("Unsupported JEI ingredient type for partition: {}", ingredient.getClass().getName());

        return ItemStack.EMPTY;
    }

    /**
     * Convert any JEI ingredient to an ItemStack for use with storage buses.
     * Uses storage bus-specific error messages.
     */
    public static ItemStack convertJeiIngredientForStorageBus(Object ingredient, boolean isFluidBus, boolean isEssentiaBus) {
        if (ingredient instanceof ItemStack) {
            ItemStack itemStack = (ItemStack) ingredient;

            // For essentia buses, try to extract essentia from containers (phials, jars, etc.)
            if (isEssentiaBus) {
                ItemStack essentiaRep = ThaumicEnergisticsIntegration.tryConvertEssentiaContainerToAspect(itemStack);

                if (!essentiaRep.isEmpty()) return essentiaRep;

                // If it's not an essentia container, reject it
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentTranslation("cellterminal.error.essentia_bus_item")
                );

                return ItemStack.EMPTY;
            }

            if (isFluidBus) {
                FluidStack contained = FluidUtil.getFluidContained(itemStack);

                if (contained == null) {
                    Minecraft.getMinecraft().player.sendMessage(
                        new TextComponentTranslation("cellterminal.error.fluid_bus_item")
                    );

                    return ItemStack.EMPTY;
                }

                IStorageChannel<IAEFluidStack> fluidChannel =
                    AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
                IAEFluidStack aeFluidStack = fluidChannel.createStack(contained);

                if (aeFluidStack == null) return ItemStack.EMPTY;

                return aeFluidStack.asItemStackRepresentation();
            }

            // Item storage bus - just return the item
            return itemStack;
        }

        if (ingredient instanceof FluidStack) {
            if (isEssentiaBus) {
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentTranslation("cellterminal.error.essentia_bus_fluid")
                );

                return ItemStack.EMPTY;
            }

            if (!isFluidBus) {
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentTranslation("cellterminal.error.item_bus_fluid")
                );

                return ItemStack.EMPTY;
            }

            FluidStack fluidStack = (FluidStack) ingredient;
            IStorageChannel<IAEFluidStack> fluidChannel =
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            IAEFluidStack aeFluidStack = fluidChannel.createStack(fluidStack);

            if (aeFluidStack == null) return ItemStack.EMPTY;

            return aeFluidStack.asItemStackRepresentation();
        }

        CellTerminal.LOGGER.warn("Unsupported JEI ingredient type for storage bus partition: {}", ingredient.getClass().getName());

        return ItemStack.EMPTY;
    }

    public static List<IGhostIngredientHandler.Target<?>> getPhantomTargets(
            int currentTab, PopupCellPartition partitionPopup,
            List<PartitionSlotTarget> partitionSlotTargets,
            List<StorageBusPartitionSlotTarget> storageBusPartitionSlotTargets,
            PartitionCallback callback, StorageBusPartitionCallback storageBusCallback) {

        if (partitionPopup != null) return partitionPopup.getGhostTargets();

        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();

        // Cell partition tab (tab 2)
        if (currentTab == 2) {
            for (PartitionSlotTarget slot : partitionSlotTargets) {
                targets.add(new IGhostIngredientHandler.Target<Object>() {
                    @Override
                    public Rectangle getArea() {
                        return new Rectangle(slot.x, slot.y, slot.width, slot.height);
                    }

                    @Override
                    public void accept(Object ing) {
                        ItemStack stack = convertJeiIngredientToItemStack(ing, slot.cell.isFluid(), slot.cell.isEssentia());

                        if (stack.isEmpty()) return;

                        callback.onAddPartitionItem(slot.cell, slot.slotIndex, stack);
                    }
                });
            }
        }

        // Storage bus partition tab (tab 4)
        if (currentTab == 4) {
            for (StorageBusPartitionSlotTarget slot : storageBusPartitionSlotTargets) {
                targets.add(new IGhostIngredientHandler.Target<Object>() {
                    @Override
                    public Rectangle getArea() {
                        return new Rectangle(slot.x, slot.y, slot.width, slot.height);
                    }

                    @Override
                    public void accept(Object ing) {
                        // Use storage bus-specific conversion with correct bus type flags
                        ItemStack stack = convertJeiIngredientForStorageBus(
                            ing, slot.storageBus.isFluid(), slot.storageBus.isEssentia());

                        if (stack.isEmpty()) return;

                        storageBusCallback.onAddStorageBusPartitionItem(slot.storageBus, slot.slotIndex, stack);
                    }
                });
            }
        }

        return targets;
    }
}
