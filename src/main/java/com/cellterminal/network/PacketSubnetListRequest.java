package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.container.ContainerCellTerminalBase;


/**
 * Packet sent from client to server to request subnet list refresh.
 * The server will respond with PacketSubnetListUpdate.
 */
public class PacketSubnetListRequest implements IMessage {

    public PacketSubnetListRequest() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // No data needed
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // No data needed
    }

    public static class Handler implements IMessageHandler<PacketSubnetListRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketSubnetListRequest message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                if (ctx.getServerHandler().player.openContainer instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase container = (ContainerCellTerminalBase) ctx.getServerHandler().player.openContainer;
                    container.requestSubnetRefresh();
                }
            });

            return null;
        }
    }
}
