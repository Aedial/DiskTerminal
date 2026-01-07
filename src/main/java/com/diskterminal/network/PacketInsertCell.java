package com.diskterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.diskterminal.container.ContainerDiskTerminal;
import com.diskterminal.container.ContainerPortableDiskTerminal;


/**
 * Packet sent from client to server to insert a held cell into a storage.
 */
public class PacketInsertCell implements IMessage {

    private long storageId;
    private int targetSlot; // -1 for first available

    public PacketInsertCell() {
    }

    public PacketInsertCell(long storageId, int targetSlot) {
        this.storageId = storageId;
        this.targetSlot = targetSlot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageId = buf.readLong();
        this.targetSlot = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageId);
        buf.writeInt(targetSlot);
    }

    public static class Handler implements IMessageHandler<PacketInsertCell, IMessage> {

        @Override
        public IMessage onMessage(PacketInsertCell message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerDiskTerminal) {
                    ContainerDiskTerminal diskContainer = (ContainerDiskTerminal) container;
                    diskContainer.handleInsertCell(message.storageId, message.targetSlot, player);
                } else if (container instanceof ContainerPortableDiskTerminal) {
                    ContainerPortableDiskTerminal portableContainer = (ContainerPortableDiskTerminal) container;
                    portableContainer.handleInsertCell(message.storageId, message.targetSlot, player);
                }
            });

            return null;
        }
    }
}
