package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.container.ContainerCellTerminalBase;


/**
 * Packet for modifying partition of a storage bus connection in the subnet overview.
 * Identifies the target by subnet ID + connection position + side.
 */
public class PacketSubnetPartitionAction implements IMessage {

    public enum Action {
        ADD_ITEM,
        REMOVE_ITEM,
        SET_ALL_FROM_CONTENTS,
        CLEAR_ALL,
        TOGGLE_ITEM,
        /** Set partition from the subnet's entire ME storage inventory (for outbound connections). */
        SET_ALL_FROM_SUBNET_INVENTORY
    }

    private long subnetId;
    private long pos;
    private int side;
    private Action action;
    private int partitionSlot;
    private ItemStack itemStack;

    public PacketSubnetPartitionAction() {
        this.itemStack = ItemStack.EMPTY;
    }

    /**
     * Actions that don't need a specific partition slot or item (CLEAR_ALL, SET_ALL_FROM_CONTENTS).
     */
    public PacketSubnetPartitionAction(long subnetId, long pos, int side, Action action) {
        this(subnetId, pos, side, action, -1, ItemStack.EMPTY);
    }

    /**
     * REMOVE_ITEM action on a specific partition slot.
     */
    public PacketSubnetPartitionAction(long subnetId, long pos, int side, Action action, int partitionSlot) {
        this(subnetId, pos, side, action, partitionSlot, ItemStack.EMPTY);
    }

    /**
     * ADD_ITEM action on a specific partition slot with an item.
     */
    public PacketSubnetPartitionAction(long subnetId, long pos, int side, Action action,
                                       int partitionSlot, ItemStack itemStack) {
        this.subnetId = subnetId;
        this.pos = pos;
        this.side = side;
        this.action = action;
        this.partitionSlot = partitionSlot;
        this.itemStack = itemStack != null ? itemStack : ItemStack.EMPTY;
    }

    /**
     * TOGGLE_ITEM action without specific partition slot.
     */
    public PacketSubnetPartitionAction(long subnetId, long pos, int side, Action action, ItemStack itemStack) {
        this(subnetId, pos, side, action, -1, itemStack);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.subnetId = buf.readLong();
        this.pos = buf.readLong();
        this.side = buf.readInt();
        this.action = Action.values()[buf.readByte()];
        this.partitionSlot = buf.readInt();

        if (buf.readBoolean()) {
            NBTTagCompound nbt = ByteBufUtils.readTag(buf);
            this.itemStack = new ItemStack(nbt);
        } else {
            this.itemStack = ItemStack.EMPTY;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(subnetId);
        buf.writeLong(pos);
        buf.writeInt(side);
        buf.writeByte(action.ordinal());
        buf.writeInt(partitionSlot);

        if (!itemStack.isEmpty()) {
            buf.writeBoolean(true);
            NBTTagCompound nbt = new NBTTagCompound();
            itemStack.writeToNBT(nbt);
            ByteBufUtils.writeTag(buf, nbt);
        } else {
            buf.writeBoolean(false);
        }
    }

    public static class Handler implements IMessageHandler<PacketSubnetPartitionAction, IMessage> {

        @Override
        public IMessage onMessage(PacketSubnetPartitionAction message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                if (ctx.getServerHandler().player.openContainer instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase container =
                        (ContainerCellTerminalBase) ctx.getServerHandler().player.openContainer;
                    container.handleSubnetPartitionAction(
                        message.subnetId, message.pos, message.side,
                        message.action, message.partitionSlot, message.itemStack);
                }
            });

            return null;
        }
    }
}
