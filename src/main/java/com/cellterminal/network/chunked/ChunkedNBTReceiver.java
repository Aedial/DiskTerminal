package com.cellterminal.network.chunked;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cellterminal.CellTerminal;


/**
 * Client-side reassembler for chunked NBT payloads.
 * <p>
 * Maintains one in-flight buffer per ({@code channel}). When a chunk arrives with a session ID
 * different from the one currently buffered for that channel, the previous buffer is discarded
 * (old session aborted). When all chunks of a session are present, the byte slices are concatenated,
 * gzip-decompressed, parsed as NBT and dispatched to the channel's
 * {@link PayloadHandler}.
 * <p>
 * Threading: all calls go through {@link #acceptChunk} on the client main thread (scheduled by
 * {@link PacketNBTChunk.Handler}), so no synchronization is needed.
 */
@SideOnly(Side.CLIENT)
public final class ChunkedNBTReceiver {

    private static final Map<String, Assembler> inflight = new HashMap<>();

    private ChunkedNBTReceiver() {}

    public static void acceptChunk(PacketNBTChunk chunk) {
        String channel = chunk.getChannel();
        Assembler assembler = inflight.get(channel);

        // New session: start fresh. This abandons any partially-received older session.
        if (assembler == null || assembler.sessionId != chunk.getSessionId()) {
            assembler = new Assembler(chunk.getSessionId(), chunk.getTotalChunks(), chunk.getMode());
            inflight.put(channel, assembler);
        }

        if (chunk.getChunkIndex() < 0 || chunk.getChunkIndex() >= assembler.totalChunks) {
            CellTerminal.LOGGER.warn("Discarding out-of-range chunk {} for channel {} (total={})",
                chunk.getChunkIndex(), channel, assembler.totalChunks);
            return;
        }

        assembler.parts[chunk.getChunkIndex()] = chunk.getPayload();
        assembler.received++;

        if (assembler.received < assembler.totalChunks) return;

        // All chunks present: assemble and dispatch.
        inflight.remove(channel);

        try {
            NBTTagCompound nbt = decode(assembler);
            PayloadHandler handler = PayloadDispatcher.get(channel);

            if (handler == null) {
                CellTerminal.LOGGER.warn("No handler registered for chunked payload channel: {}", channel);
                return;
            }

            handler.onPayload(assembler.mode, nbt);
        } catch (IOException e) {
            CellTerminal.LOGGER.error("Failed to decode chunked payload for channel " + channel, e);
        }
    }

    private static NBTTagCompound decode(Assembler assembler) throws IOException {
        // Concatenate all chunk byte arrays then gzip-decompress + read NBT.
        // We avoid a single big intermediate buffer by streaming the parts via SequenceInputStream;
        // but with at most a handful of chunks, a flat byte[] is simpler and fast enough.
        int total = 0;
        for (byte[] part : assembler.parts) total += part.length;

        byte[] joined = new byte[total];
        int offset = 0;
        for (byte[] part : assembler.parts) {
            System.arraycopy(part, 0, joined, offset, part.length);
            offset += part.length;
        }

        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(joined));
             DataInputStream dis = new DataInputStream(gzis)) {
            return CompressedStreamTools.read(dis, NBTSizeTracker.INFINITE);
        }
    }

    private static final class Assembler {
        final long sessionId;
        final int totalChunks;
        final PayloadMode mode;
        final byte[][] parts;
        int received;

        Assembler(long sessionId, int totalChunks, PayloadMode mode) {
            this.sessionId = sessionId;
            this.totalChunks = totalChunks;
            this.mode = mode;
            this.parts = new byte[totalChunks][];
        }
    }
}
