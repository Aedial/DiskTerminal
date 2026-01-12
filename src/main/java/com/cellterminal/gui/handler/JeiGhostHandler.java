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

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;

import mezz.jei.api.gui.IGhostIngredientHandler;

import com.cellterminal.CellTerminal;
import com.cellterminal.client.CellInfo;
import com.cellterminal.gui.PopupCellPartition;


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
     * Convert any JEI ingredient to an ItemStack for use with AE2 cells.
     */
    public static ItemStack convertJeiIngredientToItemStack(Object ingredient, boolean isFluidCell) {
        if (ingredient instanceof ItemStack) {
            ItemStack itemStack = (ItemStack) ingredient;

            if (isFluidCell) {
                FluidStack contained = net.minecraftforge.fluids.FluidUtil.getFluidContained(itemStack);

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
            if (isFluidCell) {
                Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentTranslation("cellterminal.error.fluid_cell_item")
                );

                return ItemStack.EMPTY;
            }

            EnchantmentData enchantData = (EnchantmentData) ingredient;

            return ItemEnchantedBook.getEnchantedItemStack(enchantData);
        }

        CellTerminal.LOGGER.warn("Unsupported JEI ingredient type for partition: {}", ingredient.getClass().getName());

        return ItemStack.EMPTY;
    }

    public static List<IGhostIngredientHandler.Target<?>> getPhantomTargets(
            int currentTab, PopupCellPartition partitionPopup,
            List<PartitionSlotTarget> partitionSlotTargets, PartitionCallback callback) {

        if (partitionPopup != null) return partitionPopup.getGhostTargets();

        if (currentTab != 2) return new ArrayList<>();

        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();

        for (PartitionSlotTarget slot : partitionSlotTargets) {
            targets.add(new IGhostIngredientHandler.Target<Object>() {
                @Override
                public Rectangle getArea() {
                    return new Rectangle(slot.x, slot.y, slot.width, slot.height);
                }

                @Override
                public void accept(Object ing) {
                    ItemStack stack = convertJeiIngredientToItemStack(ing, slot.cell.isFluid());

                    if (stack.isEmpty()) return;

                    callback.onAddPartitionItem(slot.cell, slot.slotIndex, stack);
                }
            });
        }

        return targets;
    }
}
