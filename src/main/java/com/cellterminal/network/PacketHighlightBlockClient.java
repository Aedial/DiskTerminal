package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.client.BlockHighlightRenderer;


/**
 * Packet sent from server to client to highlight a block with a glowing outline.
 */
public class PacketHighlightBlockClient implements IMessage {

    private BlockPos pos;
    private int dimension;

    public PacketHighlightBlockClient() {
    }

    public PacketHighlightBlockClient(BlockPos pos, int dimension) {
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

    public static class Handler implements IMessageHandler<PacketHighlightBlockClient, IMessage> {

        @Override
        public IMessage onMessage(PacketHighlightBlockClient message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                // Only highlight if in same dimension
                if (Minecraft.getMinecraft().player.dimension == message.dimension) {
                    BlockHighlightRenderer.addHighlight(message.pos, 15000); // 15 seconds
                }
            });

            return null;
        }
    }
}
