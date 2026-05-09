package com.cellterminal.network.chunked;

import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Client-side registry mapping channel names to payload handlers.
 * <p>
 * Handlers are registered once during init (typically by the GUI/data-manager owner) and looked
 * up by {@link ChunkedNBTReceiver} when a complete payload becomes available.
 * <p>
 * If no handler is registered for an incoming channel, the payload is logged and discarded.
 */
@SideOnly(Side.CLIENT)
public final class PayloadDispatcher {

    private static final Map<String, PayloadHandler> handlers = new HashMap<>();

    private PayloadDispatcher() {}

    /**
     * Register a handler for the given channel. Replaces any existing handler.
     */
    public static void register(String channel, PayloadHandler handler) {
        handlers.put(channel, handler);
    }

    /**
     * Remove the handler for the given channel, if any.
     */
    public static void unregister(String channel) {
        handlers.remove(channel);
    }

    /**
     * Look up the handler for a channel.
     * @return the handler, or null if none is registered
     */
    public static PayloadHandler get(String channel) {
        return handlers.get(channel);
    }
}
