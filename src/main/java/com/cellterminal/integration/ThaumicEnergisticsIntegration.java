package com.cellterminal.integration;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.helpers.ItemHandlerUtil;

import com.cellterminal.CellTerminal;


/**
 * Integration handler for Thaumic Energistics mod.
 * Provides support for Essentia cells in the Cell Terminal.
 * All Thaumic Energistics interactions are wrapped in @Optional to prevent class loading issues.
 */
public class ThaumicEnergisticsIntegration {

    private static final String MODID = "thaumicenergistics";
    private static Boolean modLoaded = null;

    /**
     * Check if Thaumic Energistics is loaded.
     */
    public static boolean isModLoaded() {
        if (modLoaded == null) modLoaded = Loader.isModLoaded(MODID);

        return modLoaded;
    }

    /**
     * Try to get cell data from an Essentia cell.
     * Returns null if not an Essentia cell or if Thaumic Energistics is not loaded.
     */
    public static NBTTagCompound tryPopulateEssentiaCell(ICellHandler cellHandler, ItemStack cellStack) {
        if (!isModLoaded()) return null;

        return tryPopulateEssentiaCellInternal(cellHandler, cellStack);
    }

    @Optional.Method(modid = MODID)
    private static NBTTagCompound tryPopulateEssentiaCellInternal(ICellHandler cellHandler, ItemStack cellStack) {
        try {
            // Get the Essentia storage channel
            IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaChannel =
                AEApi.instance().storage().getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);

            if (essentiaChannel == null) return null;

            ICellInventoryHandler<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaCellHandler =
                cellHandler.getCellInventory(cellStack, null, essentiaChannel);

            if (essentiaCellHandler == null) return null;

            ICellInventory<thaumicenergistics.api.storage.IAEEssentiaStack> cellInv = essentiaCellHandler.getCellInv();
            if (cellInv == null) return null;

            NBTTagCompound cellData = new NBTTagCompound();

            // Mark as Essentia cell
            cellData.setBoolean("isEssentia", true);

            // Basic cell stats
            cellData.setLong("usedBytes", cellInv.getUsedBytes());
            cellData.setLong("totalBytes", cellInv.getTotalBytes());
            cellData.setLong("usedTypes", cellInv.getStoredItemTypes());
            cellData.setLong("totalTypes", cellInv.getTotalItemTypes());
            cellData.setLong("storedItemCount", cellInv.getStoredItemCount());

            // Get partition (config inventory) - Essentia cells use dummy aspect items
            IItemHandler configInv = cellInv.getConfigInventory();
            if (configInv != null) {
                NBTTagList partitionList = new NBTTagList();
                for (int i = 0; i < configInv.getSlots(); i++) {
                    ItemStack partItem = configInv.getStackInSlot(i);
                    NBTTagCompound partNbt = new NBTTagCompound();
                    partNbt.setInteger("slot", i);

                    if (!partItem.isEmpty()) partItem.writeToNBT(partNbt);

                    partitionList.appendTag(partNbt);
                }

                cellData.setTag("partition", partitionList);
            }

            // Get stored essentia (contents preview) - convert to ItemStack representation (DummyAspect)
            IItemList<thaumicenergistics.api.storage.IAEEssentiaStack> contents =
                cellInv.getAvailableItems(essentiaChannel.createList());

            NBTTagList contentsList = new NBTTagList();
            int count = 0;
            for (thaumicenergistics.api.storage.IAEEssentiaStack stack : contents) {
                if (count >= 63) break;

                // Convert to ItemStack representation (DummyAspect) for client display
                ItemStack itemRep = stack.asItemStackRepresentation();
                if (!itemRep.isEmpty()) {
                    NBTTagCompound stackNbt = new NBTTagCompound();
                    itemRep.writeToNBT(stackNbt);
                    // Store the essentia amount for display purposes
                    stackNbt.setLong("essentiaAmount", stack.getStackSize());
                    contentsList.appendTag(stackNbt);
                    count++;
                }
            }

            cellData.setTag("contents", contentsList);

            return cellData;
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to get Essentia cell data: " + e.getMessage());

            return null;
        }
    }

    /**
     * Try to get the Essentia cell inventory handler for partition modifications.
     * Returns an object array: [configInv, essentiaChannel, essentiaCellHandler] or null if not Essentia.
     */
    public static Object[] tryGetEssentiaConfigInventory(ICellHandler cellHandler, ItemStack cellStack) {
        if (!isModLoaded()) return null;

        return tryGetEssentiaConfigInventoryInternal(cellHandler, cellStack);
    }

    @Optional.Method(modid = MODID)
    private static Object[] tryGetEssentiaConfigInventoryInternal(ICellHandler cellHandler, ItemStack cellStack) {
        try {
            IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaChannel =
                AEApi.instance().storage().getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);

            if (essentiaChannel == null) return null;

            ICellInventoryHandler<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaCellHandler =
                cellHandler.getCellInventory(cellStack, null, essentiaChannel);

            if (essentiaCellHandler == null || essentiaCellHandler.getCellInv() == null) return null;

            IItemHandler configInv = essentiaCellHandler.getCellInv().getConfigInventory();
            if (configInv == null) return null;

            return new Object[] { configInv, essentiaChannel, essentiaCellHandler };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Set all partition slots from cell contents for Essentia cells.
     */
    public static void setAllFromEssentiaContents(IItemHandler configInv, Object[] essentiaData) {
        if (!isModLoaded() || essentiaData == null || essentiaData.length < 3) return;

        setAllFromEssentiaContentsInternal(configInv, essentiaData);
    }

    @Optional.Method(modid = MODID)
    @SuppressWarnings("unchecked")
    private static void setAllFromEssentiaContentsInternal(IItemHandler configInv, Object[] essentiaData) {
        try {
            IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaChannel =
                (IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack>) essentiaData[1];
            ICellInventoryHandler<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaCellHandler =
                (ICellInventoryHandler<thaumicenergistics.api.storage.IAEEssentiaStack>) essentiaData[2];

            if (essentiaCellHandler.getCellInv() == null) return;

            IItemList<thaumicenergistics.api.storage.IAEEssentiaStack> contents =
                essentiaCellHandler.getCellInv().getAvailableItems(essentiaChannel.createList());

            int slot = 0;
            for (thaumicenergistics.api.storage.IAEEssentiaStack stack : contents) {
                if (slot >= configInv.getSlots()) break;

                ItemHandlerUtil.setStackInSlot(configInv, slot++, stack.asItemStackRepresentation());
            }
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to set Essentia partition from contents: " + e.getMessage());
        }
    }

    /**
     * Try to convert an ItemStack (e.g., phial) containing essentia to its DummyAspect representation.
     * This is used for partition GUI when dragging essentia containers onto essentia cells.
     *
     * @param itemStack The item that may contain essentia (phial, jar, crystal, etc.)
     * @return The DummyAspect ItemStack representation, or ItemStack.EMPTY if not an essentia container
     */
    public static ItemStack tryConvertEssentiaContainerToAspect(ItemStack itemStack) {
        if (!isModLoaded()) return ItemStack.EMPTY;

        return tryConvertEssentiaContainerToAspectInternal(itemStack);
    }

    @Optional.Method(modid = MODID)
    private static ItemStack tryConvertEssentiaContainerToAspectInternal(ItemStack itemStack) {
        try {
            // Check if item is an essentia container (phials, jars, crystals, etc.)
            if (!(itemStack.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem)) {
                return ItemStack.EMPTY;
            }

            thaumcraft.api.aspects.IEssentiaContainerItem containerItem =
                (thaumcraft.api.aspects.IEssentiaContainerItem) itemStack.getItem();
            thaumcraft.api.aspects.AspectList aspects = containerItem.getAspects(itemStack);

            if (aspects == null || aspects.size() < 1) return ItemStack.EMPTY;

            // Get the primary aspect
            thaumcraft.api.aspects.Aspect aspect = aspects.getAspects()[0];

            if (aspect == null) return ItemStack.EMPTY;

            // Create an IAEEssentiaStack and get its item representation (DummyAspect)
            IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaChannel =
                AEApi.instance().storage().getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);

            if (essentiaChannel == null) return ItemStack.EMPTY;

            // The createStack method accepts Aspect objects directly
            thaumicenergistics.api.storage.IAEEssentiaStack aeEssentiaStack = essentiaChannel.createStack(aspect);

            if (aeEssentiaStack == null) return ItemStack.EMPTY;

            return aeEssentiaStack.asItemStackRepresentation();
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to convert essentia container to aspect: " + e.getMessage());

            return ItemStack.EMPTY;
        }
    }

    /**
     * Check if an ItemStack is an essentia container (phial, jar, crystal, etc.).
     *
     * @param itemStack The item to check
     * @return true if the item is an essentia container
     */
    public static boolean isEssentiaContainer(ItemStack itemStack) {
        if (!isModLoaded()) return false;

        return isEssentiaContainerInternal(itemStack);
    }

    @Optional.Method(modid = MODID)
    private static boolean isEssentiaContainerInternal(ItemStack itemStack) {
        try {
            return itemStack.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if an object is an essentia-related JEI ingredient.
     * This includes Thaumic Energistics IAEEssentiaStack or Thaumcraft Aspect objects.
     *
     * @param ingredient The JEI ingredient to check
     * @return true if the ingredient represents essentia
     */
    public static boolean isEssentiaIngredient(Object ingredient) {
        if (!isModLoaded() || ingredient == null) return false;

        return isEssentiaIngredientInternal(ingredient);
    }

    @Optional.Method(modid = MODID)
    private static boolean isEssentiaIngredientInternal(Object ingredient) {
        try {
            // Check for IAEEssentiaStack
            if (ingredient instanceof thaumicenergistics.api.storage.IAEEssentiaStack) return true;

            // Check for Thaumcraft Aspect
            if (ingredient instanceof thaumcraft.api.aspects.Aspect) return true;

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Try to convert a JEI ingredient to an essentia ItemStack representation.
     * Handles IAEEssentiaStack and Thaumcraft Aspect objects.
     *
     * @param ingredient The JEI ingredient to convert
     * @return ItemStack representation of the essentia, or empty if not essentia
     */
    public static ItemStack tryConvertJeiIngredientToEssentia(Object ingredient) {
        if (!isModLoaded() || ingredient == null) return ItemStack.EMPTY;

        return tryConvertJeiIngredientToEssentiaInternal(ingredient);
    }

    @Optional.Method(modid = MODID)
    private static ItemStack tryConvertJeiIngredientToEssentiaInternal(Object ingredient) {
        try {
            IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaChannel =
                AEApi.instance().storage().getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);

            if (essentiaChannel == null) return ItemStack.EMPTY;

            // Handle IAEEssentiaStack directly
            if (ingredient instanceof thaumicenergistics.api.storage.IAEEssentiaStack) {
                thaumicenergistics.api.storage.IAEEssentiaStack essentiaStack =
                    (thaumicenergistics.api.storage.IAEEssentiaStack) ingredient;

                return essentiaStack.asItemStackRepresentation();
            }

            // Handle Thaumcraft Aspect
            if (ingredient instanceof thaumcraft.api.aspects.Aspect) {
                thaumcraft.api.aspects.Aspect aspect = (thaumcraft.api.aspects.Aspect) ingredient;
                thaumicenergistics.api.storage.IAEEssentiaStack aeEssentiaStack = essentiaChannel.createStack(aspect);

                if (aeEssentiaStack != null) return aeEssentiaStack.asItemStackRepresentation();
            }

            return ItemStack.EMPTY;
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to convert JEI ingredient to essentia: " + e.getMessage());

            return ItemStack.EMPTY;
        }
    }
}
