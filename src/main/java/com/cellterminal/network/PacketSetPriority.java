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
 * Can be sent from the Cell Terminal GUI.
 */
public class PacketSetPriority implements IMessage {

    private long storageId;
    private int priority;

    public PacketSetPriority() {
    }

    /**
     * Constructor for Cell Terminal GUI priority change.
     */
    public PacketSetPriority(long storageId, int priority) {
        this.storageId = storageId;
        this.priority = priority;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageId = buf.readLong();
        this.priority = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageId);
        buf.writeInt(priority);
    }

    public static class Handler implements IMessageHandler<PacketSetPriority, IMessage> {

        @Override
        public IMessage onMessage(PacketSetPriority message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                handleGuiPriority(player, message);
            });

            return null;
        }

        private void handleGuiPriority(EntityPlayerMP player, PacketSetPriority message) {
            Container container = player.openContainer;

            if (!(container instanceof ContainerCellTerminalBase)) return;

            ContainerCellTerminalBase cellContainer = (ContainerCellTerminalBase) container;
            cellContainer.handleSetPriority(message.storageId, message.priority);
        }
    }
}
