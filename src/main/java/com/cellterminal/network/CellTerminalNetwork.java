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

        // Client -> Server: Add upgrade to a cell
        INSTANCE.registerMessage(
            PacketUpgradeCell.Handler.class,
            PacketUpgradeCell.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Tab change notification (for storage bus polling)
        INSTANCE.registerMessage(
            PacketTabChange.Handler.class,
            PacketTabChange.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Storage bus partition modification actions
        INSTANCE.registerMessage(
            PacketStorageBusPartitionAction.Handler.class,
            PacketStorageBusPartitionAction.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Storage bus IO mode toggle
        INSTANCE.registerMessage(
            PacketStorageBusIOMode.Handler.class,
            PacketStorageBusIOMode.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Add upgrade to a storage bus
        INSTANCE.registerMessage(
            PacketUpgradeStorageBus.Handler.class,
            PacketUpgradeStorageBus.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Extract upgrade from a cell or storage bus
        INSTANCE.registerMessage(
            PacketExtractUpgrade.Handler.class,
            PacketExtractUpgrade.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Slot limit preference change
        INSTANCE.registerMessage(
            PacketSlotLimitChange.Handler.class,
            PacketSlotLimitChange.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Execute a network tool action
        INSTANCE.registerMessage(
            PacketNetworkToolAction.Handler.class,
            PacketNetworkToolAction.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Request subnet list refresh
        INSTANCE.registerMessage(
            PacketSubnetListRequest.Handler.class,
            PacketSubnetListRequest.class,
            packetId++,
            Side.SERVER
        );

        // Server -> Client: Subnet list update
        INSTANCE.registerMessage(
            PacketSubnetListUpdate.Handler.class,
            PacketSubnetListUpdate.class,
            packetId++,
            Side.CLIENT
        );

        // Client -> Server: Subnet action (rename, favorite)
        INSTANCE.registerMessage(
            PacketSubnetAction.Handler.class,
            PacketSubnetAction.class,
            packetId++,
            Side.SERVER
        );

        // Client -> Server: Switch network view (main or subnet)
        INSTANCE.registerMessage(
            PacketSwitchNetwork.Handler.class,
            PacketSwitchNetwork.class,
            packetId++,
            Side.SERVER
        );
    }
}
