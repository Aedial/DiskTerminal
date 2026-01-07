package com.diskterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;


/**
 * Packet sent from client to server, then broadcast to all clients in range
 * to highlight a block with a glowing outline for 15 seconds.
 */
public class PacketHighlightBlock implements IMessage {

    private BlockPos pos;
    private int dimension;

    public PacketHighlightBlock() {
    }

    public PacketHighlightBlock(BlockPos pos, int dimension) {
        this.pos = pos;
        this.dimension = dimension;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.dimension = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(dimension);
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getDimension() {
        return dimension;
    }

    public static class Handler implements IMessageHandler<PacketHighlightBlock, IMessage> {

        @Override
        public IMessage onMessage(PacketHighlightBlock message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                // Only process if player is in the same dimension
                if (player.dimension != message.dimension) return;

                // Broadcast to player for client-side highlight rendering
                DiskTerminalNetwork.INSTANCE.sendTo(
                    new PacketHighlightBlockClient(message.pos, message.dimension),
                    player
                );
            });

            return null;
        }
    }
}
