package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.container.ContainerCellTerminal;
import com.cellterminal.container.ContainerWirelessCellTerminal;


/**
 * Packet sent from client to server to add an upgrade to a cell.
 * The upgrade item is taken from the player's held item, or from a specific inventory slot
 * if fromSlot is >= 0.
 */
public class PacketUpgradeCell implements IMessage {

    private long storageId;
    private int cellSlot;
    private boolean shiftClick;  // If true, apply to first cell without this upgrade
    private int fromSlot;        // Inventory slot to take upgrade from (-1 = cursor)

    public PacketUpgradeCell() {
    }

    /**
     * Create an upgrade request for a specific cell, taking from cursor.
     * @param storageId The storage containing the cell
     * @param cellSlot The slot of the cell in the storage
     * @param shiftClick If true, apply to first visible cell that doesn't have this upgrade
     */
    public PacketUpgradeCell(long storageId, int cellSlot, boolean shiftClick) {
        this(storageId, cellSlot, shiftClick, -1);
    }

    /**
     * Create an upgrade request for a specific cell.
     * @param storageId The storage containing the cell
     * @param cellSlot The slot of the cell in the storage
     * @param shiftClick If true, apply to first visible cell that doesn't have this upgrade
     * @param fromSlot Inventory slot to take upgrade from (-1 = cursor)
     */
    public PacketUpgradeCell(long storageId, int cellSlot, boolean shiftClick, int fromSlot) {
        this.storageId = storageId;
        this.cellSlot = cellSlot;
        this.shiftClick = shiftClick;
        this.fromSlot = fromSlot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageId = buf.readLong();
        this.cellSlot = buf.readInt();
        this.shiftClick = buf.readBoolean();
        this.fromSlot = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageId);
        buf.writeInt(cellSlot);
        buf.writeBoolean(shiftClick);
        buf.writeInt(fromSlot);
    }

    public long getStorageId() {
        return storageId;
    }

    public int getCellSlot() {
        return cellSlot;
    }

    public boolean isShiftClick() {
        return shiftClick;
    }

    public int getFromSlot() {
        return fromSlot;
    }

    public static class Handler implements IMessageHandler<PacketUpgradeCell, IMessage> {

        @Override
        public IMessage onMessage(PacketUpgradeCell message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerCellTerminal) {
                    ContainerCellTerminal cellContainer = (ContainerCellTerminal) container;
                    cellContainer.handleUpgradeCell(
                        player,
                        message.getStorageId(),
                        message.getCellSlot(),
                        message.isShiftClick(),
                        message.getFromSlot()
                    );
                } else if (container instanceof ContainerWirelessCellTerminal) {
                    ContainerWirelessCellTerminal wirelessContainer = (ContainerWirelessCellTerminal) container;
                    wirelessContainer.handleUpgradeCell(
                        player,
                        message.getStorageId(),
                        message.getCellSlot(),
                        message.isShiftClick(),
                        message.getFromSlot()
                    );
                }
            });

            return null;
        }
    }
}
