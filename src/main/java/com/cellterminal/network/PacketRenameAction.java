package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.helpers.ICustomNameObject;
import appeng.tile.grid.AENetworkInvTile;

import com.cellterminal.CellTerminal;
import com.cellterminal.container.ContainerCellTerminalBase;
import com.cellterminal.container.handler.CellActionHandler;
import com.cellterminal.container.handler.CellDataHandler;
import com.cellterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.cellterminal.gui.rename.RenameTargetType;


/**
 * Packet sent from client to server to rename a storage device, cell, or storage bus.
 *
 * Target types:
 * - STORAGE: Rename a drive/ME chest (uses ICustomNameObject on TileEntity)
 * - CELL: Rename a cell item (uses ItemStack.setStackDisplayName, like an anvil)
 * - STORAGE_BUS: Rename a storage bus part (uses ICustomNameObject on IPart)
 */
public class PacketRenameAction implements IMessage {

    private RenameTargetType targetType;
    private long primaryId;    // Storage ID or storage bus ID
    private int secondaryId;   // Cell slot index (for CELL type), -1 for others
    private String newName;

    public PacketRenameAction() {}

    public PacketRenameAction(RenameTargetType targetType, long primaryId, int secondaryId, String newName) {
        this.targetType = targetType;
        this.primaryId = primaryId;
        this.secondaryId = secondaryId;
        this.newName = newName != null ? newName : "";
    }

    /**
     * Rename a storage device (ME Drive / ME Chest).
     */
    public static PacketRenameAction renameStorage(long storageId, String newName) {
        return new PacketRenameAction(RenameTargetType.STORAGE, storageId, -1, newName);
    }

    /**
     * Rename a cell item (like an anvil rename).
     */
    public static PacketRenameAction renameCell(long storageId, int cellSlot, String newName) {
        return new PacketRenameAction(RenameTargetType.CELL, storageId, cellSlot, newName);
    }

    /**
     * Rename a storage bus.
     */
    public static PacketRenameAction renameStorageBus(long storageBusId, String newName) {
        return new PacketRenameAction(RenameTargetType.STORAGE_BUS, storageBusId, -1, newName);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.targetType = RenameTargetType.values()[buf.readInt()];
        this.primaryId = buf.readLong();
        this.secondaryId = buf.readInt();
        this.newName = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(targetType.ordinal());
        buf.writeLong(primaryId);
        buf.writeInt(secondaryId);
        ByteBufUtils.writeUTF8String(buf, newName);
    }

    public static class Handler implements IMessageHandler<PacketRenameAction, IMessage> {

        @Override
        public IMessage onMessage(PacketRenameAction message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                EntityPlayer player = ctx.getServerHandler().player;

                if (!(player.openContainer instanceof ContainerCellTerminalBase)) return;

                ContainerCellTerminalBase container = (ContainerCellTerminalBase) player.openContainer;

                switch (message.targetType) {
                    case STORAGE:
                        handleStorageRename(container, message.primaryId, message.newName);
                        break;
                    case CELL:
                        handleCellRename(container, message.primaryId, message.secondaryId, message.newName);
                        break;
                    case STORAGE_BUS:
                        handleStorageBusRename(container, message.primaryId, message.newName);
                        break;
                    default:
                        break;
                }
            });

            return null;
        }

        private void handleStorageRename(ContainerCellTerminalBase container, long storageId, String newName) {
            ContainerCellTerminalBase.StorageTracker tracker = container.getStorageTracker(storageId);
            if (tracker == null) return;

            TileEntity tile = tracker.tile;

            // AE2's AEBaseTile implements ICustomNameObject
            if (!(tile instanceof ICustomNameObject)) return;

            ICustomNameObject nameable = (ICustomNameObject) tile;
            String trimmed = newName.trim();
            nameable.setCustomName(trimmed.isEmpty() ? null : trimmed);
            tile.markDirty();

            // Trigger refresh so the client sees the updated name
            container.requestFullRefresh();
        }

        private void handleCellRename(ContainerCellTerminalBase container, long storageId, int cellSlot, String newName) {
            ContainerCellTerminalBase.StorageTracker tracker = container.getStorageTracker(storageId);
            if (tracker == null) return;

            IChestOrDrive storage = tracker.storage;
            IItemHandler cellInventory = CellDataHandler.getCellInventory(storage);
            if (cellInventory == null) return;
            if (cellSlot < 0 || cellSlot >= cellInventory.getSlots()) return;

            ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
            if (cellStack.isEmpty()) return;

            // Rename the ItemStack like an anvil does
            String trimmed = newName.trim();
            if (trimmed.isEmpty()) {
                cellStack.clearCustomName();
            } else {
                cellStack.setStackDisplayName(trimmed);
            }

            // Write the modified cell back to persist the name change
            CellActionHandler.forceCellHandlerRefresh(cellInventory, cellSlot, cellStack);

            TileEntity tile = (TileEntity) storage;
            tile.markDirty();

            // Trigger refresh so the client sees the updated name
            container.requestFullRefresh();
        }

        private void handleStorageBusRename(ContainerCellTerminalBase container, long storageBusId, String newName) {
            StorageBusTracker tracker = container.getStorageBusTracker(storageBusId);
            if (tracker == null) return;

            // The storageBus object could be a PartStorageBus or other part type
            if (!(tracker.storageBus instanceof ICustomNameObject)) {
                CellTerminal.LOGGER.debug("Storage bus {} does not implement ICustomNameObject", storageBusId);
                return;
            }

            ICustomNameObject nameable = (ICustomNameObject) tracker.storageBus;
            String trimmed = newName.trim();
            nameable.setCustomName(trimmed.isEmpty() ? null : trimmed);

            if (tracker.hostTile != null) tracker.hostTile.markDirty();

            // Trigger storage bus refresh
            container.requestStorageBusRefresh();
        }
    }
}
