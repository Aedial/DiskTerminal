package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.container.ContainerCellTerminalBase;


/**
 * Packet sent from client to server to toggle storage bus IO mode (access restriction).
 * Cycles through: READ_WRITE -> READ -> WRITE -> READ_WRITE
 */
public class PacketStorageBusIOMode implements IMessage {

    private long storageBusId;

    public PacketStorageBusIOMode() {
    }

    public PacketStorageBusIOMode(long storageBusId) {
        this.storageBusId = storageBusId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageBusId = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageBusId);
    }

    public static class Handler implements IMessageHandler<PacketStorageBusIOMode, IMessage> {

        @Override
        public IMessage onMessage(PacketStorageBusIOMode message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                if (ctx.getServerHandler().player.openContainer instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase container = (ContainerCellTerminalBase) ctx.getServerHandler().player.openContainer;
                    container.handleStorageBusIOModeToggle(message.storageBusId);
                }
            });

            return null;
        }
    }
}
