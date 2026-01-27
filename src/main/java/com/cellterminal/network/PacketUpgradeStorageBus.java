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
    private int fromSlot;  // Inventory slot to take upgrade from (-1 = cursor)

    public PacketUpgradeStorageBus() {
    }

    /**
     * Create an upgrade request for a storage bus, taking from cursor.
     * @param storageBusId The storage bus to upgrade
     */
    public PacketUpgradeStorageBus(long storageBusId) {
        this(storageBusId, -1);
    }

    /**
     * Create an upgrade request for a storage bus.
     * @param storageBusId The storage bus to upgrade
     * @param fromSlot Inventory slot to take upgrade from (-1 = cursor)
     */
    public PacketUpgradeStorageBus(long storageBusId, int fromSlot) {
        this.storageBusId = storageBusId;
        this.fromSlot = fromSlot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageBusId = buf.readLong();
        this.fromSlot = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageBusId);
        buf.writeInt(fromSlot);
    }

    public long getStorageBusId() {
        return storageBusId;
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
                        message.getFromSlot()
                    );
                }
            });

            return null;
        }
    }
}
