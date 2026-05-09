package com.cellterminal.network.chunked;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Generic server-to-client packet carrying one chunk of a logical NBT payload.
 * <p>
 * Multiple chunks make up one logical payload, identified by ({@code channel}, {@code sessionId}).
 * Chunks of the same session arrive in order on the network, but the assembler keys by index for
 * resilience. When all chunks have arrived, the assembler decompresses and dispatches the payload
 * to the registered {@link com.cellterminal.network.chunked.PayloadDispatcher channel handler}.
 * <p>
 * Wire format:
 * <ul>
 *   <li>String channel</li>
 *   <li>int sessionId</li>
 *   <li>int chunkIndex</li>
 *   <li>int totalChunks</li>
 *   <li>byte mode (see {@link PayloadMode})</li>
 *   <li>int payloadLen</li>
 *   <li>byte[payloadLen] payload</li>
 * </ul>
 * The payload is the raw bytes of the gzip-compressed NBT (a slice of, when split).
 * <p>
 * Default routing: {@link Side#CLIENT}. Registered in {@link com.cellterminal.network.CellTerminalNetwork}.
 */
public class PacketNBTChunk implements IMessage {

    private String channel;
    private long sessionId;
    private int chunkIndex;
    private int totalChunks;
    private PayloadMode mode;
    private byte[] payload;

    public PacketNBTChunk() {
        this.channel = "";
        this.payload = new byte[0];
        this.mode = PayloadMode.FULL;
    }

    public PacketNBTChunk(String channel, long sessionId, int chunkIndex, int totalChunks,
                          PayloadMode mode, byte[] payload) {
        this.channel = channel;
        this.sessionId = sessionId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.mode = mode;
        this.payload = payload;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.channel = ByteBufUtils.readUTF8String(buf);
        this.sessionId = buf.readLong();
        this.chunkIndex = buf.readInt();
        this.totalChunks = buf.readInt();
        this.mode = PayloadMode.fromId(buf.readByte());
        int len = buf.readInt();
        this.payload = new byte[len];
        buf.readBytes(this.payload);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.channel);
        buf.writeLong(this.sessionId);
        buf.writeInt(this.chunkIndex);
        buf.writeInt(this.totalChunks);
        buf.writeByte(this.mode.getId());
        buf.writeInt(this.payload.length);
        buf.writeBytes(this.payload);
    }

    public String getChannel() { return channel; }
    public long getSessionId() { return sessionId; }
    public int getChunkIndex() { return chunkIndex; }
    public int getTotalChunks() { return totalChunks; }
    public PayloadMode getMode() { return mode; }
    public byte[] getPayload() { return payload; }

    public static class Handler implements IMessageHandler<PacketNBTChunk, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketNBTChunk message, MessageContext ctx) {
            // Reassembly and dispatch happen on the client thread to keep NBT/GUI
            // interaction off the netty thread.
            Minecraft.getMinecraft().addScheduledTask(
                () -> ChunkedNBTReceiver.acceptChunk(message)
            );

            return null;
        }
    }
}
