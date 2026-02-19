package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.container.ContainerCellTerminalBase;


/**
 * Packet sent from client to server to switch the terminal view to a different network.
 * 
 * Used when the player clicks on a subnet in the subnet overview to view that subnet's contents,
 * or when navigating back to the main network.
 */
public class PacketSwitchNetwork implements IMessage {

    /**
     * The network ID to switch to.
     * 0 = main network, >0 = subnet ID
     */
    private long networkId;

    public PacketSwitchNetwork() {
    }

    public PacketSwitchNetwork(long networkId) {
        this.networkId = networkId;
    }

    /**
     * Create a packet to switch to the main network.
     */
    public static PacketSwitchNetwork toMainNetwork() {
        return new PacketSwitchNetwork(0);
    }

    /**
     * Create a packet to switch to a specific subnet.
     */
    public static PacketSwitchNetwork toSubnet(long subnetId) {
        return new PacketSwitchNetwork(subnetId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.networkId = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(networkId);
    }

    public long getNetworkId() {
        return networkId;
    }

    public boolean isMainNetwork() {
        return networkId == 0;
    }

    public static class Handler implements IMessageHandler<PacketSwitchNetwork, IMessage> {

        @Override
        public IMessage onMessage(PacketSwitchNetwork message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                if (ctx.getServerHandler().player.openContainer instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase container = (ContainerCellTerminalBase) ctx.getServerHandler().player.openContainer;
                    container.switchNetwork(message.networkId);
                }
            });

            return null;
        }
    }
}
