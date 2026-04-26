package com.cellterminal.network.chunked;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.cellterminal.CellTerminal;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.network.CellTerminalNetwork;


/**
 * Server-side helper that serializes an NBT compound, gzip-compresses it, splits it into chunks
 * sized according to {@link CellTerminalServerConfig#getMaxChunkBytes()}, and sends the chunks
 * to a player as {@link PacketNBTChunk} messages on a given logical channel.
 * <p>
 * Each call increments the per-channel session counter so the receiver can tell payloads apart
 * and discard old in-flight assemblies.
 * <p>
 * Threading: must be called from the server main thread (typical container tick context).
 */
public final class ChunkedNBTSender {

    // Per-channel monotonic session counter. Shared across all players: receivers key by
    // channel and only care that the value differs from the previously-buffered one.
    private static final Map<String, AtomicLong> sessionCounters = new HashMap<>();

    private ChunkedNBTSender() {}

    /**
     * Send an NBT payload on the given channel as a chunked stream.
     *
     * @param player target player
     * @param channel logical channel name
     * @param mode FULL or DELTA (informational; the receiver uses it to decide how to apply the payload)
     * @param data the NBT data to send. The compound itself is what arrives on the client side.
     */
    public static void send(EntityPlayerMP player, String channel, PayloadMode mode, NBTTagCompound data) {
        byte[] compressed;

        try {
            compressed = compress(data);
        } catch (IOException e) {
            CellTerminal.LOGGER.error("Failed to compress NBT payload for channel " + channel, e);
            return;
        }

        int maxChunkBytes = CellTerminalServerConfig.getInstance().getMaxChunkBytes();
        if (maxChunkBytes <= 0) maxChunkBytes = 524288;

        int totalChunks = Math.max(1, (compressed.length + maxChunkBytes - 1) / maxChunkBytes);
        long sessionId = nextSessionId(channel);

        for (int i = 0; i < totalChunks; i++) {
            int start = i * maxChunkBytes;
            int end = Math.min(start + maxChunkBytes, compressed.length);
            byte[] slice = new byte[end - start];
            System.arraycopy(compressed, start, slice, 0, slice.length);

            CellTerminalNetwork.INSTANCE.sendTo(
                new PacketNBTChunk(channel, sessionId, i, totalChunks, mode, slice),
                player
            );
        }
    }

    private static byte[] compress(NBTTagCompound data) throws IOException {
        // TODO: should we use GZip or a direct bytebuffer?
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gzos)) {
            CompressedStreamTools.write(data, dos);
        }
        return baos.toByteArray();
    }

    private static long nextSessionId(String channel) {
        AtomicLong counter = sessionCounters.get(channel);
        if (counter == null) {
            counter = new AtomicLong();
            sessionCounters.put(channel, counter);
        }
        return counter.incrementAndGet();
    }
}
