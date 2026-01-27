package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.container.ContainerCellTerminalBase;


/**
 * Packet sent from client to server to extract an upgrade from a cell or storage bus.
 * The upgrade is placed in the player's hand or inventory.
 */
public class PacketExtractUpgrade implements IMessage {

    public enum TargetType {
        CELL,
        STORAGE_BUS
    }

    private TargetType targetType;
    private long targetId;
    private int cellSlot;      // Only used for cells
    private int upgradeIndex;
    private boolean toInventory;  // If true, put in inventory; if false, put in hand

    public PacketExtractUpgrade() {
    }

    /**
     * Create an extract request for a cell upgrade.
     * @param storageId The storage containing the cell
     * @param cellSlot The slot of the cell in the storage
     * @param upgradeIndex The upgrade slot index to extract from
     * @param toInventory If true, put in inventory; if false, put in hand
     */
    public static PacketExtractUpgrade forCell(long storageId, int cellSlot, int upgradeIndex, boolean toInventory) {
        PacketExtractUpgrade packet = new PacketExtractUpgrade();
        packet.targetType = TargetType.CELL;
        packet.targetId = storageId;
        packet.cellSlot = cellSlot;
        packet.upgradeIndex = upgradeIndex;
        packet.toInventory = toInventory;

        return packet;
    }

    /**
     * Create an extract request for a storage bus upgrade.
     * @param storageBusId The storage bus to extract from
     * @param upgradeIndex The upgrade slot index to extract from
     * @param toInventory If true, put in inventory; if false, put in hand
     */
    public static PacketExtractUpgrade forStorageBus(long storageBusId, int upgradeIndex, boolean toInventory) {
        PacketExtractUpgrade packet = new PacketExtractUpgrade();
        packet.targetType = TargetType.STORAGE_BUS;
        packet.targetId = storageBusId;
        packet.cellSlot = 0;
        packet.upgradeIndex = upgradeIndex;
        packet.toInventory = toInventory;

        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.targetType = buf.readBoolean() ? TargetType.STORAGE_BUS : TargetType.CELL;
        this.targetId = buf.readLong();
        this.cellSlot = buf.readInt();
        this.upgradeIndex = buf.readInt();
        this.toInventory = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(targetType == TargetType.STORAGE_BUS);
        buf.writeLong(targetId);
        buf.writeInt(cellSlot);
        buf.writeInt(upgradeIndex);
        buf.writeBoolean(toInventory);
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public long getTargetId() {
        return targetId;
    }

    public int getCellSlot() {
        return cellSlot;
    }

    public int getUpgradeIndex() {
        return upgradeIndex;
    }

    public boolean isToInventory() {
        return toInventory;
    }

    public static class Handler implements IMessageHandler<PacketExtractUpgrade, IMessage> {

        @Override
        public IMessage onMessage(PacketExtractUpgrade message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase cellContainer = (ContainerCellTerminalBase) container;
                    cellContainer.handleExtractUpgrade(
                        player,
                        message.getTargetType(),
                        message.getTargetId(),
                        message.getCellSlot(),
                        message.getUpgradeIndex(),
                        message.isToInventory()
                    );
                }
            });

            return null;
        }
    }
}
