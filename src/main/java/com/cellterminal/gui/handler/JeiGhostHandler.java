package com.cellterminal.gui.handler;

import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;

import com.cellterminal.CellTerminal;
import com.cellterminal.client.StorageType;
import com.cellterminal.gui.overlay.MessageHelper;
import com.cellterminal.integration.MekanismEnergisticsIntegration;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;


/**
 * Handles JEI Ghost Ingredient support for Cell Terminal GUI.
 */
public class JeiGhostHandler {

    // TODO: starting to become a nightmare to dispatch error messages for every type -> every type
    /**
     * Convert any JEI ingredient to an ItemStack for use with AE2 cells.
     */
    public static ItemStack convertJeiIngredientToItemStack(Object ingredient, StorageType cellType) {
        if (ingredient instanceof ItemStack) {
            ItemStack itemStack = (ItemStack) ingredient;

            // For gas cells, try to extract gas from containers
            if (cellType.isGas()) {
                ItemStack gasRep = MekanismEnergisticsIntegration.tryConvertGasContainerToGas(itemStack);

                if (!gasRep.isEmpty()) return gasRep;

                // If it's not a gas container, reject it
                MessageHelper.error("cellterminal.error.gas_cell_item");

                return ItemStack.EMPTY;
            }

            // For essentia cells, try to extract essentia from containers (phials, jars, etc.)
            if (cellType.isEssentia()) {
                ItemStack essentiaRep = ThaumicEnergisticsIntegration.tryConvertEssentiaContainerToAspect(itemStack);

                if (!essentiaRep.isEmpty()) return essentiaRep;

                // If it's not an essentia container, reject it
                MessageHelper.error("cellterminal.error.essentia_cell_item");

                return ItemStack.EMPTY;
            }

            if (cellType.isFluid()) {
                FluidStack contained = FluidUtil.getFluidContained(itemStack);

                if (contained == null) {
                    MessageHelper.error("cellterminal.error.fluid_cell_item");

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
            if (cellType.isGas()) {
                MessageHelper.error("cellterminal.error.gas_cell_fluid");

                return ItemStack.EMPTY;
            }

            if (cellType.isEssentia()) {
                MessageHelper.error("cellterminal.error.essentia_cell_fluid");

                return ItemStack.EMPTY;
            }

            if (!cellType.isFluid()) {
                MessageHelper.error("cellterminal.error.item_cell_fluid");

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
            if (cellType.isFluid() || cellType.isEssentia() || cellType.isGas()) {
                String errorKey = cellType.isFluid() ? "cellterminal.error.fluid_cell_item"
                    : cellType.isGas() ? "cellterminal.error.gas_cell_item"
                    : "cellterminal.error.essentia_cell_item";
                MessageHelper.error(errorKey);

                return ItemStack.EMPTY;
            }

            EnchantmentData enchantData = (EnchantmentData) ingredient;

            return ItemEnchantedBook.getEnchantedItemStack(enchantData);
        }

        // Gas ingredients from JEI (GasStack or IAEGasStack from MekanismEnergistics)
        if (MekanismEnergisticsIntegration.isGasIngredient(ingredient)) {
            if (!cellType.isGas()) {
                String errorKey = cellType.isFluid() ? "cellterminal.error.fluid_cell_gas"
                    : cellType.isEssentia() ? "cellterminal.error.essentia_cell_gas"
                    : "cellterminal.error.item_cell_gas";
                MessageHelper.error(errorKey);

                return ItemStack.EMPTY;
            }

            return MekanismEnergisticsIntegration.tryConvertJeiIngredientToGas(ingredient);
        }

        CellTerminal.LOGGER.warn("Unsupported JEI ingredient type for partition: {}", ingredient.getClass().getName());

        return ItemStack.EMPTY;
    }

    /**
     * Convert any JEI ingredient to an ItemStack for use with storage buses.
     * Uses storage bus-specific error messages.
     */
    public static ItemStack convertJeiIngredientForStorageBus(Object ingredient, StorageType busType) {
        if (ingredient instanceof ItemStack) {
            ItemStack itemStack = (ItemStack) ingredient;

            // For gas buses, try to extract gas from containers
            if (busType.isGas()) {
                ItemStack gasRep = MekanismEnergisticsIntegration.tryConvertGasContainerToGas(itemStack);

                if (!gasRep.isEmpty()) return gasRep;

                // If it's not a gas container, reject it
                MessageHelper.error("cellterminal.error.gas_bus_item");

                return ItemStack.EMPTY;
            }

            // For essentia buses, try to extract essentia from containers (phials, jars, etc.)
            if (busType.isEssentia()) {
                ItemStack essentiaRep = ThaumicEnergisticsIntegration.tryConvertEssentiaContainerToAspect(itemStack);

                if (!essentiaRep.isEmpty()) return essentiaRep;

                // If it's not an essentia container, reject it
                MessageHelper.error("cellterminal.error.essentia_bus_item");

                return ItemStack.EMPTY;
            }

            if (busType.isFluid()) {
                FluidStack contained = FluidUtil.getFluidContained(itemStack);

                if (contained == null) {
                    MessageHelper.error("cellterminal.error.fluid_bus_item");

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
            if (busType.isGas()) {
                MessageHelper.error("cellterminal.error.gas_bus_fluid");

                return ItemStack.EMPTY;
            }

            if (busType.isEssentia()) {
                MessageHelper.error("cellterminal.error.essentia_bus_fluid");

                return ItemStack.EMPTY;
            }

            if (!busType.isFluid()) {
                MessageHelper.error("cellterminal.error.item_bus_fluid");

                return ItemStack.EMPTY;
            }

            FluidStack fluidStack = (FluidStack) ingredient;
            IStorageChannel<IAEFluidStack> fluidChannel =
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            IAEFluidStack aeFluidStack = fluidChannel.createStack(fluidStack);

            if (aeFluidStack == null) return ItemStack.EMPTY;

            return aeFluidStack.asItemStackRepresentation();
        }

        // Gas ingredients from JEI (GasStack or IAEGasStack from MekanismEnergistics)
        if (MekanismEnergisticsIntegration.isGasIngredient(ingredient)) {
            if (!busType.isGas()) {
                String errorKey = busType.isFluid() ? "cellterminal.error.fluid_bus_gas"
                    : busType.isEssentia() ? "cellterminal.error.essentia_bus_gas"
                    : "cellterminal.error.item_bus_gas";
                MessageHelper.error(errorKey);

                return ItemStack.EMPTY;
            }

            return MekanismEnergisticsIntegration.tryConvertJeiIngredientToGas(ingredient);
        }

        CellTerminal.LOGGER.warn("Unsupported JEI ingredient type for storage bus partition: {}", ingredient.getClass().getName());

        return ItemStack.EMPTY;
    }
}
