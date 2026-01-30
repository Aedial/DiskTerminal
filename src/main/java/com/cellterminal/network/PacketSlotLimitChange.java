package com.cellterminal.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.container.ContainerCellTerminalBase;


/**
 * Packet sent from client to server to sync slot limit preferences.
 * These limits control how many item types are serialized per cell/bus.
 */
public class PacketSlotLimitChange implements IMessage {

    private int cellLimit;
    private int busLimit;

    public PacketSlotLimitChange() {
    }

    public PacketSlotLimitChange(int cellLimit, int busLimit) {
        this.cellLimit = cellLimit;
        this.busLimit = busLimit;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.cellLimit = buf.readInt();
        this.busLimit = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(cellLimit);
        buf.writeInt(busLimit);
    }

    public static class Handler implements IMessageHandler<PacketSlotLimitChange, IMessage> {

        @Override
        public IMessage onMessage(PacketSlotLimitChange message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                if (ctx.getServerHandler().player.openContainer instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase container = (ContainerCellTerminalBase) ctx.getServerHandler().player.openContainer;
                    container.setSlotLimits(message.cellLimit, message.busLimit);
                }
            });

            return null;
        }
    }
}
