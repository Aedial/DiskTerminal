package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.helpers.IPriorityHost;

import com.cellterminal.container.ContainerCellTerminalBase;


/**
 * Packet sent from client to server to modify storage priority.
 * Can be sent from the Cell Terminal GUI or from the Priority Wand.
 */
public class PacketSetPriority implements IMessage {

    private long storageId;
    private int priority;
    private boolean fromWand;
    private BlockPos wandTargetPos;
    private int wandTargetDim;

    public PacketSetPriority() {
    }

    /**
     * Constructor for Cell Terminal GUI priority change.
     */
    public PacketSetPriority(long storageId, int priority) {
        this.storageId = storageId;
        this.priority = priority;
        this.fromWand = false;
    }

    /**
     * Constructor for Priority Wand world interaction.
     */
    public PacketSetPriority(BlockPos pos, int dimension, int priority) {
        this.storageId = 0;
        this.priority = priority;
        this.fromWand = true;
        this.wandTargetPos = pos;
        this.wandTargetDim = dimension;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageId = buf.readLong();
        this.priority = buf.readInt();
        this.fromWand = buf.readBoolean();

        if (this.fromWand) {
            this.wandTargetPos = BlockPos.fromLong(buf.readLong());
            this.wandTargetDim = buf.readInt();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageId);
        buf.writeInt(priority);
        buf.writeBoolean(fromWand);

        if (fromWand) {
            buf.writeLong(wandTargetPos.toLong());
            buf.writeInt(wandTargetDim);
        }
    }

    public static class Handler implements IMessageHandler<PacketSetPriority, IMessage> {

        @Override
        public IMessage onMessage(PacketSetPriority message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                if (message.fromWand) {
                    handleWandPriority(player, message);
                } else {
                    handleGuiPriority(player, message);
                }
            });

            return null;
        }

        private void handleGuiPriority(EntityPlayerMP player, PacketSetPriority message) {
            Container container = player.openContainer;

            if (!(container instanceof ContainerCellTerminalBase)) return;

            ContainerCellTerminalBase cellContainer = (ContainerCellTerminalBase) container;
            cellContainer.handleSetPriority(message.storageId, message.priority);
        }

        private void handleWandPriority(EntityPlayerMP player, PacketSetPriority message) {
            if (player.dimension != message.wandTargetDim) return;

            World world = player.getServerWorld();
            TileEntity te = world.getTileEntity(message.wandTargetPos);

            if (!(te instanceof IPriorityHost)) return;

            IPriorityHost priorityHost = (IPriorityHost) te;
            priorityHost.setPriority(message.priority);
            te.markDirty();

            // Send highlight to client
            CellTerminalNetwork.INSTANCE.sendTo(
                new PacketPriorityApplied(message.wandTargetPos, message.wandTargetDim, message.priority),
                player
            );
        }
    }
}
