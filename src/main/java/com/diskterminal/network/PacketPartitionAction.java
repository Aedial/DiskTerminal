package com.diskterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.diskterminal.container.ContainerDiskTerminal;
import com.diskterminal.container.ContainerPortableDiskTerminal;


/**
 * Packet sent from client to server to modify disk partition.
 */
public class PacketPartitionAction implements IMessage {

    public enum Action {
        ADD_ITEM,
        REMOVE_ITEM,
        SET_ALL_FROM_CONTENTS,
        CLEAR_ALL,
        TOGGLE_ITEM
    }

    private long storageId;
    private int diskSlot;
    private Action action;
    private int partitionSlot;
    private ItemStack itemStack;

    public PacketPartitionAction() {
        this.itemStack = ItemStack.EMPTY;
    }

    public PacketPartitionAction(long storageId, int diskSlot, Action action) {
        this.storageId = storageId;
        this.diskSlot = diskSlot;
        this.action = action;
        this.partitionSlot = -1;
        this.itemStack = ItemStack.EMPTY;
    }

    public PacketPartitionAction(long storageId, int diskSlot, Action action, int partitionSlot) {
        this.storageId = storageId;
        this.diskSlot = diskSlot;
        this.action = action;
        this.partitionSlot = partitionSlot;
        this.itemStack = ItemStack.EMPTY;
    }

    public PacketPartitionAction(long storageId, int diskSlot, Action action, int partitionSlot, ItemStack itemStack) {
        this.storageId = storageId;
        this.diskSlot = diskSlot;
        this.action = action;
        this.partitionSlot = partitionSlot;
        this.itemStack = itemStack != null ? itemStack : ItemStack.EMPTY;
    }

    public PacketPartitionAction(long storageId, int diskSlot, Action action, ItemStack itemStack) {
        this.storageId = storageId;
        this.diskSlot = diskSlot;
        this.action = action;
        this.partitionSlot = -1;
        this.itemStack = itemStack != null ? itemStack : ItemStack.EMPTY;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageId = buf.readLong();
        this.diskSlot = buf.readInt();
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
        buf.writeLong(storageId);
        buf.writeInt(diskSlot);
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

    public static class Handler implements IMessageHandler<PacketPartitionAction, IMessage> {

        @Override
        public IMessage onMessage(PacketPartitionAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerDiskTerminal) {
                    ContainerDiskTerminal diskContainer = (ContainerDiskTerminal) container;
                    diskContainer.handlePartitionAction(
                        message.storageId,
                        message.diskSlot,
                        message.action,
                        message.partitionSlot,
                        message.itemStack
                    );
                } else if (container instanceof ContainerPortableDiskTerminal) {
                    ContainerPortableDiskTerminal portableContainer = (ContainerPortableDiskTerminal) container;
                    portableContainer.handlePartitionAction(
                        message.storageId,
                        message.diskSlot,
                        message.action,
                        message.partitionSlot,
                        message.itemStack
                    );
                }
            });

            return null;
        }
    }
}
