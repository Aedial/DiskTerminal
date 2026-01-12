package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.container.ContainerCellTerminal;
import com.cellterminal.container.ContainerWirelessCellTerminal;


/**
 * Packet sent from client to server to pick up a cell from a drive into the player's hand.
 * Used for tab 2/3 slot interactions (clicking cells to pick them up/swap).
 */
public class PacketPickupCell implements IMessage {

    private long storageId;
    private int cellSlot;

    public PacketPickupCell() {
    }

    public PacketPickupCell(long storageId, int cellSlot) {
        this.storageId = storageId;
        this.cellSlot = cellSlot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageId = buf.readLong();
        this.cellSlot = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageId);
        buf.writeInt(cellSlot);
    }

    public static class Handler implements IMessageHandler<PacketPickupCell, IMessage> {

        @Override
        public IMessage onMessage(PacketPickupCell message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerCellTerminal) {
                    ContainerCellTerminal cellContainer = (ContainerCellTerminal) container;
                    cellContainer.handlePickupCell(message.storageId, message.cellSlot, player);
                } else if (container instanceof ContainerWirelessCellTerminal) {
                    ContainerWirelessCellTerminal wirelessContainer = (ContainerWirelessCellTerminal) container;
                    wirelessContainer.handlePickupCell(message.storageId, message.cellSlot, player);
                }
            });

            return null;
        }
    }
}
