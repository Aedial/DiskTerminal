package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.container.ContainerCellTerminalBase;


/**
 * Packet sent from client to server to notify of tab change.
 * Used to enable/disable storage bus polling based on active tab.
 */
public class PacketTabChange implements IMessage {

    private int tabIndex;

    public PacketTabChange() {
    }

    public PacketTabChange(int tabIndex) {
        this.tabIndex = tabIndex;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.tabIndex = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tabIndex);
    }

    public static class Handler implements IMessageHandler<PacketTabChange, IMessage> {
        @Override
        public IMessage onMessage(PacketTabChange message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                if (ctx.getServerHandler().player.openContainer instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase container = (ContainerCellTerminalBase) ctx.getServerHandler().player.openContainer;
                    container.setActiveTab(message.tabIndex);
                }
            });

            return null;
        }
    }
}
