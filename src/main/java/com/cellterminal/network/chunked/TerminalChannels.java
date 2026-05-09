package com.cellterminal.network.chunked;


/**
 * Logical payload channel names used with {@link ChunkedNBTSender} / {@link PayloadDispatcher}.
 * <p>
 * Each channel is a separate stream: it has its own session counter on the server side and its
 * own assembler on the client side. This means independent payloads (e.g. storage list vs subnet
 * list) cannot stomp on each other and can be interleaved freely.
 * <p>
 * The split mirrors how the GUI consumes the data and is what enables active-tab prioritization:
 * the server can choose which channel(s) to send first based on the client's current tab.
 */
public final class TerminalChannels {

    /** Tiny header with terminalPos / terminalDim / networkId. Sent first on every refresh. */
    public static final String META = "ct:meta";

    /** Storage list (drives, chests). Heaviest channel; carries cell partitions / contents. */
    public static final String STORAGES = "ct:storages";

    /** Storage bus list. Polled separately when on a storage bus tab. */
    public static final String BUSES = "ct:buses";

    /** Temp cell area contents. Bounded to 16 slots so always small but uses the same protocol. */
    public static final String TEMP_CELLS = "ct:temp";

    /** Subnet list. Sent on demand when the subnet overview tab is active. */
    public static final String SUBNETS = "ct:subnets";

    private TerminalChannels() {}
}
