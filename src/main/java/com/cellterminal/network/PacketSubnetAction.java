package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.container.ContainerCellTerminalBase;
import com.cellterminal.container.handler.SubnetDataHandler;


/**
 * Packet sent from client to server to perform an action on a subnet.
 * Actions include renaming and toggling favorite status.
 */
public class PacketSubnetAction implements IMessage {

    private long subnetId;
    private SubnetDataHandler.SubnetAction action;
    private NBTTagCompound data;

    public PacketSubnetAction() {
        this.data = new NBTTagCompound();
    }

    public PacketSubnetAction(long subnetId, SubnetDataHandler.SubnetAction action, NBTTagCompound data) {
        this.subnetId = subnetId;
        this.action = action;
        this.data = data != null ? data : new NBTTagCompound();
    }

    /**
     * Create a rename action packet.
     */
    public static PacketSubnetAction rename(long subnetId, String newName) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("name", newName);

        return new PacketSubnetAction(subnetId, SubnetDataHandler.SubnetAction.RENAME, data);
    }

    /**
     * Create a toggle favorite action packet.
     */
    public static PacketSubnetAction toggleFavorite(long subnetId, boolean favorite) {
        NBTTagCompound data = new NBTTagCompound();
        data.setBoolean("favorite", favorite);

        return new PacketSubnetAction(subnetId, SubnetDataHandler.SubnetAction.TOGGLE_FAVORITE, data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.subnetId = buf.readLong();
        this.action = SubnetDataHandler.SubnetAction.values()[buf.readInt()];
        this.data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(subnetId);
        buf.writeInt(action.ordinal());
        ByteBufUtils.writeTag(buf, data);
    }

    public static class Handler implements IMessageHandler<PacketSubnetAction, IMessage> {

        @Override
        public IMessage onMessage(PacketSubnetAction message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                if (ctx.getServerHandler().player.openContainer instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase container = (ContainerCellTerminalBase) ctx.getServerHandler().player.openContainer;
                    container.handleSubnetAction(message.subnetId, message.action, message.data);
                }
            });

            return null;
        }
    }
}
