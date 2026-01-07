package com.diskterminal.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.diskterminal.Tags;


/**
 * Network handler for disk terminal packets.
 */
public class DiskTerminalNetwork {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MODID);

    private static int packetId = 0;

    public static void init() {
        // Server -> Client: Update disk terminal GUI
        INSTANCE.registerMessage(
            PacketDiskTerminalUpdate.Handler.class,
            PacketDiskTerminalUpdate.class,
            packetId++,
            Side.CLIENT
        );

        // Client -> Server: Partition modification actions
        INSTANCE.registerMessage(
            PacketPartitionAction.Handler.class,
            PacketPartitionAction.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Eject disk from drive
        INSTANCE.registerMessage(
            PacketEjectDisk.Handler.class,
            PacketEjectDisk.class,
            packetId++,
            Side.SERVER
        );
    }
}
