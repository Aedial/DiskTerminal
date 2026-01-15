package com.cellterminal.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.cellterminal.Tags;


/**
 * Network handler for cell terminal packets.
 */
public class CellTerminalNetwork {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MODID);

    private static int packetId = 0;

    public static void init() {
        // Server -> Client: Update cell terminal GUI
        INSTANCE.registerMessage(
            PacketCellTerminalUpdate.Handler.class,
            PacketCellTerminalUpdate.class,
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

        // Client -> Server: Eject cell from drive
        INSTANCE.registerMessage(
            PacketEjectCell.Handler.class,
            PacketEjectCell.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Insert held cell into storage
        INSTANCE.registerMessage(
            PacketInsertCell.Handler.class,
            PacketInsertCell.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Pickup cell into player's hand
        INSTANCE.registerMessage(
            PacketPickupCell.Handler.class,
            PacketPickupCell.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Request block highlight
        INSTANCE.registerMessage(
            PacketHighlightBlock.Handler.class,
            PacketHighlightBlock.class,
            packetId++,
            Side.SERVER
        );

        // Server -> Client: Highlight a block
        INSTANCE.registerMessage(
            PacketHighlightBlockClient.Handler.class,
            PacketHighlightBlockClient.class,
            packetId++,
            Side.CLIENT
        );

        // Client -> Server: Set priority on a storage block
        INSTANCE.registerMessage(
            PacketSetPriority.Handler.class,
            PacketSetPriority.class,
            packetId++,
            Side.SERVER
        );

        // Server -> Client: Priority applied confirmation (green highlight)
        INSTANCE.registerMessage(
            PacketPriorityApplied.Handler.class,
            PacketPriorityApplied.class,
            packetId++,
            Side.CLIENT
        );

        // Client -> Server: Add upgrade to a cell
        INSTANCE.registerMessage(
            PacketUpgradeCell.Handler.class,
            PacketUpgradeCell.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Set Priority Wand stored priority
        INSTANCE.registerMessage(
            PacketSetWandPriority.Handler.class,
            PacketSetWandPriority.class,
            packetId++,
            Side.SERVER
        );
    }
}
