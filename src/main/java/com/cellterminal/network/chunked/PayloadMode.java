package com.cellterminal.network.chunked;


/**
 * Encoding mode of a chunked payload.
 * Stored as a single byte in {@link PacketNBTChunk}.
 */
public enum PayloadMode {
    /**
     * Full payload: receiver should clear any previous state for the channel and
     * apply this payload as the new ground truth.
     */
    FULL((byte) 0),

    /**
     * Delta payload: receiver should keep its current state and apply incremental
     * add/remove/update operations from this payload. The exact format is defined
     * by each channel's handler.
     */
    DELTA((byte) 1);

    private final byte id;

    PayloadMode(byte id) {
        this.id = id;
    }

    public byte getId() {
        return id;
    }

    public static PayloadMode fromId(byte id) {
        for (PayloadMode mode : values()) {
            if (mode.id == id) return mode;
        }

        return FULL;
    }
}
