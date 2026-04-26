package com.cellterminal.network.chunked;

import net.minecraft.nbt.NBTTagCompound;


/**
 * Handler invoked on the client when a complete chunked payload has been reassembled.
 */
@FunctionalInterface
public interface PayloadHandler {

    /**
     * Called when a payload arrives for the registered channel.
     *
     * @param mode whether this payload is a full snapshot or an incremental delta
     * @param data the decoded NBT payload
     */
    void onPayload(PayloadMode mode, NBTTagCompound data);
}
