package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.container.ContainerCellTerminalBase;


/**
 * Packet sent from client to server to add an upgrade to a storage bus.
 * The upgrade item is taken from the player's held item, or from a specific inventory slot
 * if fromSlot is >= 0.
 */
public class PacketUpgradeStorageBus implements IMessage {

    private long storageBusId;
    private boolean shiftClick;  // If true, apply to first storage bus that accepts this upgrade
    private int fromSlot;        // Inventory slot to take upgrade from (-1 = cursor)

    public PacketUpgradeStorageBus() {
    }

    /**
     * Create an upgrade request for a storage bus, taking from cursor.
     * @param storageBusId The storage bus to upgrade
     * @param shiftClick If true, apply to first visible storage bus that accepts this upgrade
     */
    public PacketUpgradeStorageBus(long storageBusId, boolean shiftClick) {
        this(storageBusId, shiftClick, -1);
    }

    /**
     * Create an upgrade request for a storage bus.
     * @param storageBusId The storage bus to upgrade
     * @param shiftClick If true, apply to first visible storage bus that accepts this upgrade
     * @param fromSlot Inventory slot to take upgrade from (-1 = cursor)
     */
    public PacketUpgradeStorageBus(long storageBusId, boolean shiftClick, int fromSlot) {
        this.storageBusId = storageBusId;
        this.shiftClick = shiftClick;
        this.fromSlot = fromSlot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageBusId = buf.readLong();
        this.shiftClick = buf.readBoolean();
        this.fromSlot = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageBusId);
        buf.writeBoolean(shiftClick);
        buf.writeInt(fromSlot);
    }

    public long getStorageBusId() {
        return storageBusId;
    }

    public boolean isShiftClick() {
        return shiftClick;
    }

    public int getFromSlot() {
        return fromSlot;
    }

    public static class Handler implements IMessageHandler<PacketUpgradeStorageBus, IMessage> {

        @Override
        public IMessage onMessage(PacketUpgradeStorageBus message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase cellContainer = (ContainerCellTerminalBase) container;
                    cellContainer.handleUpgradeStorageBus(
                        player,
                        message.getStorageBusId(),
                        message.isShiftClick(),
                        message.getFromSlot()
                    );
                }
            });

            return null;
        }
    }
}
