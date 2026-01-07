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
 * Packet sent from client to server to eject a disk from a drive.
 */
public class PacketEjectDisk implements IMessage {

    private long storageId;
    private int diskSlot;

    public PacketEjectDisk() {
    }

    public PacketEjectDisk(long storageId, int diskSlot) {
        this.storageId = storageId;
        this.diskSlot = diskSlot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageId = buf.readLong();
        this.diskSlot = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageId);
        buf.writeInt(diskSlot);
    }

    public static class Handler implements IMessageHandler<PacketEjectDisk, IMessage> {

        @Override
        public IMessage onMessage(PacketEjectDisk message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerDiskTerminal) {
                    ContainerDiskTerminal diskContainer = (ContainerDiskTerminal) container;
                    diskContainer.handleEjectDisk(message.storageId, message.diskSlot, player);
                } else if (container instanceof ContainerPortableDiskTerminal) {
                    ContainerPortableDiskTerminal portableContainer = (ContainerPortableDiskTerminal) container;
                    portableContainer.handleEjectDisk(message.storageId, message.diskSlot, player);
                }
            });

            return null;
        }
    }
}
