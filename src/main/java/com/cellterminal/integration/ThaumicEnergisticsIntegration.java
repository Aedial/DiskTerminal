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
import com.cellterminal.client.StorageBusInfo;


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
     * @param cellHandler The cell handler
     * @param cellStack The cell ItemStack
     * @param slotLimit Maximum number of item types to include
     * @return NBT data for the cell, or null if not an Essentia cell
     */
    public static NBTTagCompound tryPopulateEssentiaCell(ICellHandler cellHandler, ItemStack cellStack,
                                                          int slotLimit) {
        if (!isModLoaded()) return null;

        return tryPopulateEssentiaCellInternal(cellHandler, cellStack, slotLimit);
    }

    @Optional.Method(modid = MODID)
    private static NBTTagCompound tryPopulateEssentiaCellInternal(ICellHandler cellHandler, ItemStack cellStack,
                                                                   int slotLimit) {
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
                if (count >= slotLimit) break;

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
            // If already an ItemDummyAspect, return as-is
            if (itemStack.getItem() instanceof thaumicenergistics.item.ItemDummyAspect) {
                return itemStack;
            }

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

    /**
     * Get the PartEssentiaStorageBus class for grid machine iteration.
     * Returns null if Thaumic Energistics is not loaded.
     */
    public static Class<?> getEssentiaStorageBusClass() {
        if (!isModLoaded()) return null;

        return getEssentiaStorageBusClassInternal();
    }

    @Optional.Method(modid = MODID)
    private static Class<?> getEssentiaStorageBusClassInternal() {
        try {
            return thaumicenergistics.part.PartEssentiaStorageBus.class;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create NBT data for an essentia storage bus.
     * Returns null if not an essentia storage bus or if Thaumic Energistics is not loaded.
     *
     * @param machine The grid machine (should be a PartEssentiaStorageBus)
     * @param busId The unique bus ID
     * @return NBT data for the storage bus, or null if not supported
     */
    public static NBTTagCompound tryCreateEssentiaStorageBusData(Object machine, long busId) {
        if (!isModLoaded()) return null;

        return tryCreateEssentiaStorageBusDataInternal(machine, busId);
    }

    @Optional.Method(modid = MODID)
    private static NBTTagCompound tryCreateEssentiaStorageBusDataInternal(Object machine, long busId) {
        try {
            if (!(machine instanceof thaumicenergistics.part.PartEssentiaStorageBus)) return null;

            thaumicenergistics.part.PartEssentiaStorageBus bus =
                (thaumicenergistics.part.PartEssentiaStorageBus) machine;

            net.minecraft.tileentity.TileEntity hostTile = bus.getTile();
            if (hostTile == null) return null;

            NBTTagCompound busData = new NBTTagCompound();
            busData.setLong("id", busId);
            busData.setLong("pos", hostTile.getPos().toLong());
            busData.setInteger("dim", hostTile.getWorld().provider.getDimension());
            busData.setInteger("side", bus.side.ordinal());
            busData.setInteger("priority", bus.getPriority());
            busData.setBoolean("isEssentia", true);
            // Essentia uses fixed config slots; provide explicit values
            busData.setInteger("baseConfigSlots", com.cellterminal.client.StorageBusInfo.MAX_CONFIG_SLOTS);
            busData.setInteger("slotsPerUpgrade", 0);
            busData.setInteger("maxConfigSlots", com.cellterminal.client.StorageBusInfo.MAX_CONFIG_SLOTS);

            // Essentia storage buses don't have the same upgrade types as item storage buses
            // They use speed upgrades only
            int capacityUpgrades = 0;  // Essentia buses don't have capacity upgrades
            busData.setInteger("capacityUpgrades", capacityUpgrades);

            // Access restriction - essentia buses always allow read/write
            busData.setInteger("access", 3);  // READ_WRITE

            // Connected inventory info - check for IAspectContainer
            net.minecraft.tileentity.TileEntity targetTile = hostTile.getWorld().getTileEntity(
                hostTile.getPos().offset(bus.side.getFacing()));

            if (targetTile != null) {
                // Get the block as icon
                net.minecraft.block.state.IBlockState state = hostTile.getWorld().getBlockState(
                    hostTile.getPos().offset(bus.side.getFacing()));
                ItemStack blockStack = new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));

                if (!blockStack.isEmpty()) {
                    busData.setString("connectedName", blockStack.getDisplayName());

                    NBTTagCompound iconNbt = new NBTTagCompound();
                    blockStack.writeToNBT(iconNbt);
                    busData.setTag("connectedIcon", iconNbt);
                }
            }

            // Get config (partition) from essentia filter
            thaumicenergistics.util.EssentiaFilter config = bus.getConfig();
            if (config != null) {
                NBTTagList partitionList = new NBTTagList();
                int slotIndex = 0;

                for (thaumcraft.api.aspects.Aspect aspect : config) {
                    NBTTagCompound partNbt = new NBTTagCompound();
                    partNbt.setInteger("slot", slotIndex);

                    if (aspect != null) {
                        // Convert aspect to DummyAspect item for display
                        IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaChannel =
                            AEApi.instance().storage().getStorageChannel(
                                thaumicenergistics.api.storage.IEssentiaStorageChannel.class);

                        if (essentiaChannel != null) {
                            thaumicenergistics.api.storage.IAEEssentiaStack aeStack =
                                essentiaChannel.createStack(aspect);

                            if (aeStack != null) {
                                ItemStack itemRep = aeStack.asItemStackRepresentation();
                                if (!itemRep.isEmpty()) itemRep.writeToNBT(partNbt);
                            }
                        }
                    }

                    partitionList.appendTag(partNbt);
                    slotIndex++;
                }

                busData.setTag("partition", partitionList);
            }

            // Get contents from connected IAspectContainer
            if (targetTile instanceof thaumcraft.api.aspects.IAspectContainer) {
                thaumcraft.api.aspects.IAspectContainer container =
                    (thaumcraft.api.aspects.IAspectContainer) targetTile;
                thaumcraft.api.aspects.AspectList aspects = container.getAspects();

                NBTTagList contentsList = new NBTTagList();

                if (aspects != null) {
                    int count = 0;
                    IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaChannel =
                        AEApi.instance().storage().getStorageChannel(
                            thaumicenergistics.api.storage.IEssentiaStorageChannel.class);

                    int limit = busData.hasKey("maxConfigSlots") ? busData.getInteger("maxConfigSlots")
                        : StorageBusInfo.MAX_CONFIG_SLOTS;

                    for (thaumcraft.api.aspects.Aspect aspect : aspects.getAspects()) {
                        if (aspect == null) break;
                        if (count >= limit) break;

                        int amount = aspects.getAmount(aspect);
                        if (amount <= 0) continue;

                        if (essentiaChannel != null) {
                            thaumicenergistics.api.storage.IAEEssentiaStack aeStack =
                                essentiaChannel.createStack(aspect);

                            if (aeStack != null) {
                                ItemStack itemRep = aeStack.asItemStackRepresentation();
                                if (!itemRep.isEmpty()) {
                                    NBTTagCompound stackNbt = new NBTTagCompound();
                                    itemRep.writeToNBT(stackNbt);
                                    stackNbt.setLong("Cnt", amount);
                                    contentsList.appendTag(stackNbt);
                                    count++;
                                }
                            }
                        }
                    }
                }

                busData.setTag("contents", contentsList);
            }

            // Upgrades - get from the upgrades inventory
            NBTTagList upgradeList = new NBTTagList();
            net.minecraftforge.items.IItemHandler upgradeInv = bus.getInventoryByName("upgrades");
            if (upgradeInv != null) {
                for (int i = 0; i < upgradeInv.getSlots(); i++) {
                    ItemStack upgradeStack = upgradeInv.getStackInSlot(i);
                    if (!upgradeStack.isEmpty()) {
                        NBTTagCompound upgradeNbt = new NBTTagCompound();
                        upgradeStack.writeToNBT(upgradeNbt);
                        upgradeList.appendTag(upgradeNbt);
                    }
                }
            }

            busData.setTag("upgrades", upgradeList);

            return busData;
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to create essentia storage bus data: " + e.getMessage());

            return null;
        }
    }

    /**
     * Handle partition action for essentia storage buses.
     * This method dispatches to the internal handler if Thaumic Energistics is loaded.
     */
    public static void handleEssentiaStorageBusPartition(Object storageBus,
                                                          com.cellterminal.network.PacketStorageBusPartitionAction.Action action,
                                                          int partitionSlot,
                                                          ItemStack itemStack) {
        if (!isModLoaded()) return;

        handleEssentiaStorageBusPartitionInternal(storageBus, action, partitionSlot, itemStack);
    }

    @Optional.Method(modid = MODID)
    private static void handleEssentiaStorageBusPartitionInternal(Object storageBus,
                                                                   com.cellterminal.network.PacketStorageBusPartitionAction.Action action,
                                                                   int partitionSlot,
                                                                   ItemStack itemStack) {
        try {
            if (!(storageBus instanceof thaumicenergistics.part.PartEssentiaStorageBus)) return;

            thaumicenergistics.part.PartEssentiaStorageBus bus =
                (thaumicenergistics.part.PartEssentiaStorageBus) storageBus;

            thaumicenergistics.util.EssentiaFilter config = bus.getConfig();
            if (config == null) return;

            // Convert ItemStack to Aspect if it's an essentia representation
            thaumcraft.api.aspects.Aspect aspectFromItem = null;
            // Try to get aspect from DummyAspect item or filled phial/jar
            if (!itemStack.isEmpty()) aspectFromItem = getAspectFromItem(itemStack);

            switch (action) {
                case ADD_ITEM:
                    if (partitionSlot >= 0 && aspectFromItem != null) config.setAspect(aspectFromItem, partitionSlot);
                    break;

                case REMOVE_ITEM:
                    if (partitionSlot >= 0) config.setAspect(null, partitionSlot);
                    break;

                case TOGGLE_ITEM:
                    if (aspectFromItem != null) {
                        int existingSlot = findAspectInConfig(config, aspectFromItem);
                        if (existingSlot >= 0) {
                            config.setAspect(null, existingSlot);
                        } else {
                            int emptySlot = findEmptyAspectSlot(config);
                            if (emptySlot >= 0) config.setAspect(aspectFromItem, emptySlot);
                        }
                    }
                    break;

                case SET_ALL_FROM_CONTENTS:
                    // Clear all aspects first
                    clearAspectConfig(config);
                    // Get contents from connected IAspectContainer
                    net.minecraft.tileentity.TileEntity hostTile = bus.getTile();
                    if (hostTile != null && hostTile.getWorld() != null) {
                        net.minecraft.tileentity.TileEntity targetTile = hostTile.getWorld().getTileEntity(
                            hostTile.getPos().offset(bus.side.getFacing()));
                        if (targetTile instanceof thaumcraft.api.aspects.IAspectContainer) {
                            thaumcraft.api.aspects.IAspectContainer container =
                                (thaumcraft.api.aspects.IAspectContainer) targetTile;
                            thaumcraft.api.aspects.AspectList aspects = container.getAspects();
                            if (aspects != null) {
                                for (thaumcraft.api.aspects.Aspect aspect : aspects.getAspects()) {
                                    if (aspect == null) continue;
                                    int amount = aspects.getAmount(aspect);
                                    if (amount <= 0) continue;
                                    // Check if we have room
                                    int emptySlot = findEmptyAspectSlot(config);
                                    if (emptySlot < 0) break;
                                    config.setAspect(aspect, emptySlot);
                                }
                            }
                        }
                    }
                    break;

                case CLEAR_ALL:
                    clearAspectConfig(config);
                    break;
            }
        } catch (Exception e) {
            CellTerminal.LOGGER.debug("Failed to handle essentia storage bus partition: " + e.getMessage());
        }
    }

    @Optional.Method(modid = MODID)
    private static thaumcraft.api.aspects.Aspect getAspectFromItem(ItemStack stack) {
        // Try DummyAspect item first (Thaumic Energistics representation)
        if (stack.getItem() instanceof thaumicenergistics.item.ItemDummyAspect) {
            thaumicenergistics.item.ItemDummyAspect dummyItem =
                (thaumicenergistics.item.ItemDummyAspect) stack.getItem();

            return dummyItem.getAspect(stack);
        }

        // Try phials and jars from Thaumcraft
        if (stack.getItem() instanceof thaumcraft.common.items.consumables.ItemPhial) {
            thaumcraft.common.items.consumables.ItemPhial phial =
                (thaumcraft.common.items.consumables.ItemPhial) stack.getItem();
            thaumcraft.api.aspects.AspectList aspects = phial.getAspects(stack);
            if (aspects != null && aspects.getAspects().length > 0) {
                return aspects.getAspects()[0];
            }
        }

        return null;
    }

    @Optional.Method(modid = MODID)
    private static int findAspectInConfig(thaumicenergistics.util.EssentiaFilter config, thaumcraft.api.aspects.Aspect aspect) {
        int slot = 0;
        for (thaumcraft.api.aspects.Aspect a : config) {
            if (a != null && a.equals(aspect)) return slot;
            slot++;
        }

        return -1;
    }

    @Optional.Method(modid = MODID)
    private static int findEmptyAspectSlot(thaumicenergistics.util.EssentiaFilter config) {
        int slot = 0;
        for (thaumcraft.api.aspects.Aspect a : config) {
            if (a == null) return slot;
            slot++;
        }

        return -1;
    }

    @Optional.Method(modid = MODID)
    private static void clearAspectConfig(thaumicenergistics.util.EssentiaFilter config) {
        int slot = 0;
        for (thaumcraft.api.aspects.Aspect a : config) config.setAspect(null, slot++);
    }
}
