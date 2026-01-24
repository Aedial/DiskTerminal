package com.cellterminal.container.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.AEApi;
import appeng.fluids.items.FluidDummyItem;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.helpers.ItemHandlerUtil;

import com.cells.api.IItemCompactingCell;

import com.cellterminal.integration.ThaumicEnergisticsIntegration;
import com.cellterminal.network.PacketPartitionAction;


/**
 * Handles cell-related actions: partition modifications, ejection, pickup, insertion, upgrades.
 */
public class CellActionHandler {

    /**
     * Handle partition modification for a cell.
     * @return true if the partition was modified and refresh is needed
     */
    public static boolean handlePartitionAction(IChestOrDrive storage, TileEntity tile, int cellSlot,
                                                 PacketPartitionAction.Action action,
                                                 int partitionSlot, ItemStack itemStack) {
        IItemHandler cellInventory = CellDataHandler.getCellInventory(storage);
        if (cellInventory == null) return false;

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        if (cellStack.isEmpty()) return false;

        ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
        if (cellHandler == null) return false;

        // Get config inventory for the appropriate channel type
        ConfigResult config = getConfigInventory(cellHandler, cellStack);
        if (config.configInv == null) return false;

        // Execute the action
        executePartitionAction(config.configInv, action, partitionSlot, itemStack,
                               cellHandler, cellStack, config.isFluidCell, config.essentiaData);

        // Write the modified cell back to the inventory
        // The config modification updates cellStack's NBT, but we need to persist it
        forceCellHandlerRefresh(cellInventory, cellSlot, cellStack);

        // Handle compacting cells (additional logic for compacting cell chain init)
        handleCompactingCell(cellStack, cellInventory, cellSlot, action, itemStack, config.configInv, tile.getWorld());

        tile.markDirty();

        return true;
    }

    /**
     * Eject a cell from storage to player's inventory.
     * @return true if ejected successfully
     */
    public static boolean ejectCell(IChestOrDrive storage, int cellSlot, EntityPlayer player) {
        IItemHandler cellInventory = CellDataHandler.getCellInventory(storage);
        if (cellInventory == null) return false;

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        if (cellStack.isEmpty()) return false;

        ItemStack extracted = cellInventory.extractItem(cellSlot, 1, false);
        if (extracted.isEmpty()) return false;

        if (!player.inventory.addItemStackToInventory(extracted)) player.dropItem(extracted, false);

        return true;
    }

    /**
     * Pick up a cell to player's hand or swap with held cell.
     * @return true if operation succeeded
     */
    public static boolean pickupCell(IChestOrDrive storage, int cellSlot, EntityPlayer player, boolean toInventory) {
        IItemHandler cellInventory = CellDataHandler.getCellInventory(storage);
        if (cellInventory == null) return false;

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        ItemStack heldStack = player.inventory.getItemStack();

        // Empty slot + holding cell = insert (only for hand mode)
        if (cellStack.isEmpty()) {
            if (!toInventory && !heldStack.isEmpty() && isValidCell(heldStack)) {
                ItemStack remainder = cellInventory.insertItem(cellSlot, heldStack.copy(), false);
                player.inventory.setItemStack(remainder.isEmpty() ? ItemStack.EMPTY : remainder);
                ((EntityPlayerMP) player).updateHeldItem();

                return true;
            }

            return false;
        }

        // Shift-click: to inventory
        if (toInventory) {
            ItemStack extracted = cellInventory.extractItem(cellSlot, 1, false);
            if (extracted.isEmpty()) return false;

            if (!player.inventory.addItemStackToInventory(extracted)) player.dropItem(extracted, false);

            return true;
        }

        // Regular click: swap or pickup
        if (!heldStack.isEmpty()) {
            if (!isValidCell(heldStack)) return false;

            ItemStack extracted = cellInventory.extractItem(cellSlot, 1, false);
            if (extracted.isEmpty()) return false;

            ItemStack remainder = cellInventory.insertItem(cellSlot, heldStack.copy(), false);
            if (!remainder.isEmpty()) {
                cellInventory.insertItem(cellSlot, extracted, false);

                return false;
            }

            player.inventory.setItemStack(extracted);
        } else {
            ItemStack extracted = cellInventory.extractItem(cellSlot, 1, false);
            if (extracted.isEmpty()) return false;

            player.inventory.setItemStack(extracted);
        }

        ((EntityPlayerMP) player).updateHeldItem();

        return true;
    }

    /**
     * Insert held cell into storage.
     * @return true if inserted successfully
     */
    public static boolean insertCell(IChestOrDrive storage, int targetSlot, EntityPlayer player) {
        ItemStack heldStack = player.inventory.getItemStack();
        if (heldStack.isEmpty() || !isValidCell(heldStack)) return false;

        IItemHandler cellInventory = CellDataHandler.getCellInventory(storage);
        if (cellInventory == null) return false;

        int slot = targetSlot >= 0 ? targetSlot : findEmptySlot(cellInventory);
        if (slot < 0) return false;

        ItemStack remainder = cellInventory.insertItem(slot, heldStack.copy(), false);
        if (remainder.getCount() < heldStack.getCount()) {
            player.inventory.setItemStack(remainder.isEmpty() ? ItemStack.EMPTY : remainder);
            ((EntityPlayerMP) player).updateHeldItem();

            return true;
        }

        return false;
    }

    /**
     * Insert an upgrade into a cell.
     * @return true if upgrade was inserted
     */
    public static boolean upgradeCell(IChestOrDrive storage, TileEntity tile, int cellSlot,
                                       ItemStack upgradeStack, EntityPlayer player, int fromSlot) {
        if (upgradeStack.isEmpty() || !(upgradeStack.getItem() instanceof IUpgradeModule)) return false;

        IUpgradeModule upgradeModule = (IUpgradeModule) upgradeStack.getItem();
        Upgrades upgradeType = upgradeModule.getType(upgradeStack);
        if (upgradeType == null) return false;

        IItemHandler cellInventory = CellDataHandler.getCellInventory(storage);
        if (cellInventory == null) return false;

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        if (cellStack.isEmpty() || !(cellStack.getItem() instanceof ICellWorkbenchItem)) return false;

        ICellWorkbenchItem cellItem = (ICellWorkbenchItem) cellStack.getItem();
        if (!cellItem.isEditable(cellStack)) return false;

        IItemHandler upgradesInv = cellItem.getUpgradesInventory(cellStack);
        if (upgradesInv == null) return false;

        ItemStack toInsert = upgradeStack.copy();
        toInsert.setCount(1);

        for (int slot = 0; slot < upgradesInv.getSlots(); slot++) {
            ItemStack remainder = upgradesInv.insertItem(slot, toInsert, false);
            if (remainder.isEmpty()) {
                upgradeStack.shrink(1);

                if (fromSlot >= 0) {
                    player.inventory.markDirty();
                } else {
                    ((EntityPlayerMP) player).updateHeldItem();
                }

                tile.markDirty();
                forceCellHandlerRefresh(cellInventory, cellSlot, cellStack);

                return true;
            }
        }

        return false;
    }

    // --- Helper methods ---

    private static ConfigResult getConfigInventory(ICellHandler cellHandler, ItemStack cellStack) {
        ConfigResult result = new ConfigResult();

        IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        ICellInventoryHandler<IAEItemStack> itemHandler = cellHandler.getCellInventory(cellStack, null, itemChannel);

        if (itemHandler != null) {
            if (itemHandler.getCellInv() != null) {
                result.configInv = itemHandler.getCellInv().getConfigInventory();
                result.itemHandler = itemHandler;

                return result;
            }

            // Fallback for cells with null getCellInv() (e.g., VoidCells)
            if (cellStack.getItem() instanceof ICellWorkbenchItem) {
                result.configInv = ((ICellWorkbenchItem) cellStack.getItem()).getConfigInventory(cellStack);
                result.itemHandler = itemHandler;

                return result;
            }
        }

        IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        ICellInventoryHandler<IAEFluidStack> fluidHandler = cellHandler.getCellInventory(cellStack, null, fluidChannel);

        if (fluidHandler != null) {
            if (fluidHandler.getCellInv() != null) {
                result.configInv = fluidHandler.getCellInv().getConfigInventory();
                result.fluidHandler = fluidHandler;
                result.isFluidCell = true;

                return result;
            }

            // Fallback for cells with null getCellInv() (e.g., VoidCells)
            if (cellStack.getItem() instanceof ICellWorkbenchItem) {
                result.configInv = ((ICellWorkbenchItem) cellStack.getItem()).getConfigInventory(cellStack);
                result.fluidHandler = fluidHandler;
                result.isFluidCell = true;

                return result;
            }
        }

        result.essentiaData = ThaumicEnergisticsIntegration.tryGetEssentiaConfigInventory(cellHandler, cellStack);
        if (result.essentiaData != null) result.configInv = (IItemHandler) result.essentiaData[0];

        return result;
    }

    private static void executePartitionAction(IItemHandler configInv, PacketPartitionAction.Action action,
                                                int partitionSlot, ItemStack itemStack,
                                                ICellHandler cellHandler, ItemStack cellStack,
                                                boolean isFluidCell, Object[] essentiaData) {
        // Normalize fluid stacks to remove NBT tags that would cause comparison mismatches
        ItemStack normalizedStack = isFluidCell ? normalizeFluidStack(itemStack) : itemStack;

        switch (action) {
            case ADD_ITEM:
                if (partitionSlot >= 0 && partitionSlot < configInv.getSlots() && !normalizedStack.isEmpty()) {
                    setConfigSlot(configInv, partitionSlot, normalizedStack);
                }
                break;

            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < configInv.getSlots()) {
                    setConfigSlot(configInv, partitionSlot, ItemStack.EMPTY);
                }
                break;

            case TOGGLE_ITEM:
                if (!normalizedStack.isEmpty()) {
                    int existingSlot = isFluidCell
                        ? findFluidInConfig(configInv, normalizedStack)
                        : findItemInConfig(configInv, normalizedStack);

                    if (existingSlot >= 0) {
                        setConfigSlot(configInv, existingSlot, ItemStack.EMPTY);
                    } else {
                        int emptySlot = findEmptySlot(configInv);
                        if (emptySlot >= 0) setConfigSlot(configInv, emptySlot, normalizedStack);
                    }
                }
                break;

            case SET_ALL_FROM_CONTENTS:
                clearConfig(configInv);
                setPartitionFromContents(cellHandler, cellStack, configInv, isFluidCell, essentiaData);
                break;

            case CLEAR_ALL:
                clearConfig(configInv);
                break;
        }
    }

    private static void setPartitionFromContents(ICellHandler cellHandler, ItemStack cellStack,
                                                  IItemHandler configInv, boolean isFluidCell,
                                                  Object[] essentiaData) {
        if (essentiaData != null) {
            ThaumicEnergisticsIntegration.setAllFromEssentiaContents(configInv, essentiaData);
        } else if (!isFluidCell) {
            IStorageChannel<IAEItemStack> channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            ICellInventoryHandler<IAEItemStack> handler = cellHandler.getCellInventory(cellStack, null, channel);
            if (handler != null && handler.getCellInv() != null) {
                IItemList<IAEItemStack> contents = handler.getCellInv().getAvailableItems(channel.createList());
                int slot = 0;
                for (IAEItemStack stack : contents) {
                    if (slot >= configInv.getSlots()) break;
                    setConfigSlot(configInv, slot++, stack.asItemStackRepresentation());
                }
            }
        } else {
            IStorageChannel<IAEFluidStack> channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            ICellInventoryHandler<IAEFluidStack> handler = cellHandler.getCellInventory(cellStack, null, channel);
            if (handler != null && handler.getCellInv() != null) {
                IItemList<IAEFluidStack> contents = handler.getCellInv().getAvailableItems(channel.createList());
                int slot = 0;
                for (IAEFluidStack stack : contents) {
                    if (slot >= configInv.getSlots()) break;
                    // Normalize fluid to remove NBT tags that could cause comparison issues
                    setConfigSlot(configInv, slot++, normalizeFluidStack(stack.asItemStackRepresentation()));
                }
            }
        }
    }

    /**
     * Normalize a fluid ItemStack to ensure consistent comparison.
     * Creates a new FluidDummyItem with only the fluid type (no NBT tag, standard amount).
     * This is necessary because:
     * 1. AE2's partition comparison uses AEFluidStack.equals() which includes tagCompound
     * 2. Different fluid sources (tanks, buckets) may include different NBT metadata
     * 3. Cell filtering would fail if partition NBT doesn't match cell contents NBT
     */
    private static ItemStack normalizeFluidStack(ItemStack stack) {
        FluidStack fluid = extractFluidFromStack(stack);
        if (fluid == null || fluid.getFluid() == null) return stack;

        // Create a clean FluidStack with just the fluid type, no NBT tag
        FluidStack normalized = new FluidStack(fluid.getFluid(), net.minecraftforge.fluids.Fluid.BUCKET_VOLUME);

        // Convert to FluidDummyItem representation
        IStorageChannel<IAEFluidStack> channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        IAEFluidStack aeStack = channel.createStack(normalized);
        if (aeStack == null) return stack;

        return aeStack.asItemStackRepresentation();
    }

    private static void handleCompactingCell(ItemStack cellStack, IItemHandler cellInventory, int cellSlot,
                                              PacketPartitionAction.Action action, ItemStack itemStack,
                                              IItemHandler configInv, World world) {
        if (!(cellStack.getItem() instanceof IItemCompactingCell)) return;

        forceCellHandlerRefresh(cellInventory, cellSlot, cellStack);

        ItemStack partitionItem = ItemStack.EMPTY;

        if (action == PacketPartitionAction.Action.ADD_ITEM && !itemStack.isEmpty()) {
            partitionItem = itemStack;
        } else if (action == PacketPartitionAction.Action.TOGGLE_ITEM && !itemStack.isEmpty()) {
            if (findItemInConfig(configInv, itemStack) < 0) partitionItem = itemStack;
        } else if (action == PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS) {
            for (int i = 0; i < configInv.getSlots(); i++) {
                ItemStack slot = configInv.getStackInSlot(i);
                if (!slot.isEmpty()) {
                    partitionItem = slot;
                    break;
                }
            }
        }

        ((IItemCompactingCell) cellStack.getItem()).initializeCompactingCellChain(cellStack, partitionItem, world);
    }

    private static void forceCellHandlerRefresh(IItemHandler cellInventory, int cellSlot, ItemStack cellStack) {
        if (cellInventory instanceof IItemHandlerModifiable) {
            ((IItemHandlerModifiable) cellInventory).setStackInSlot(cellSlot, cellStack);
        }
    }

    private static boolean isValidCell(ItemStack stack) {
        return AEApi.instance().registries().cell().getHandler(stack) != null;
    }

    public static int findEmptySlot(IItemHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            if (inv.getStackInSlot(i).isEmpty()) return i;
        }

        return -1;
    }

    public static int findItemInConfig(IItemHandler inv, ItemStack stack) {
        for (int i = 0; i < inv.getSlots(); i++) {
            if (ItemStack.areItemStacksEqual(inv.getStackInSlot(i), stack)) return i;
        }

        return -1;
    }

    /**
     * Find a fluid in the config inventory, comparing by fluid type only (ignoring amount and NBT).
     * This is necessary because:
     * 1. FluidDummyItem stores the fluid amount in NBT
     * 2. Some tank items include extra NBT data (capacity, etc.) that varies with fluid amount
     * Using direct Fluid instance comparison ensures only the fluid type matters.
     */
    public static int findFluidInConfig(IItemHandler inv, ItemStack stack) {
        FluidStack targetFluid = extractFluidFromStack(stack);
        if (targetFluid == null || targetFluid.getFluid() == null) return -1;

        for (int i = 0; i < inv.getSlots(); i++) {
            FluidStack slotFluid = extractFluidFromStack(inv.getStackInSlot(i));

            if (slotFluid != null && slotFluid.getFluid() == targetFluid.getFluid()) return i;
        }

        return -1;
    }

    private static FluidStack extractFluidFromStack(ItemStack stack) {
        if (stack.isEmpty()) return null;

        if (stack.getItem() instanceof FluidDummyItem) {
            return ((FluidDummyItem) stack.getItem()).getFluidStack(stack);
        }

        return net.minecraftforge.fluids.FluidUtil.getFluidContained(stack);
    }

    public static void setConfigSlot(IItemHandler inv, int slot, ItemStack stack) {
        ItemHandlerUtil.setStackInSlot(inv, slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    public static void clearConfig(IItemHandler inv) {
        ItemHandlerUtil.clear(inv);
    }

    private static class ConfigResult {
        IItemHandler configInv;
        ICellInventoryHandler<IAEItemStack> itemHandler;
        ICellInventoryHandler<IAEFluidStack> fluidHandler;
        boolean isFluidCell;
        Object[] essentiaData;
    }
}
