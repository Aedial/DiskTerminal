package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.container.ContainerCellTerminalBase;


/**
 * Packet for temp cell area operations (insert, extract, send to network).
 */
public class PacketTempCellAction implements IMessage {

    public enum Action {
        /** Insert cell from player inventory into temp area */
        INSERT,
        /** Extract cell from temp area back to player inventory */
        EXTRACT,
        /** Send cell from temp area to first available slot in ME network */
        SEND,
        /** Insert an upgrade card into a temp cell */
        UPGRADE,
        /** Swap cell in temp area with cell held on cursor */
        SWAP
    }

    private Action action;
    private int tempSlotIndex;
    private int playerSlotIndex;  // Used for INSERT
    private boolean toInventory;  // Used for EXTRACT - true to send directly to inventory (shift-click)

    public PacketTempCellAction() {
    }

    /**
     * Create a packet for EXTRACT or SEND actions.
     */
    public PacketTempCellAction(Action action, int tempSlotIndex) {
        this(action, tempSlotIndex, false);
    }

    /**
     * Create a packet for EXTRACT or SEND actions.
     * @param toInventory For EXTRACT: if true, send directly to inventory (shift-click behavior)
     */
    public PacketTempCellAction(Action action, int tempSlotIndex, boolean toInventory) {
        this.action = action;
        this.tempSlotIndex = tempSlotIndex;
        this.playerSlotIndex = -1;
        this.toInventory = toInventory;
    }

    /**
     * Create a packet for INSERT action from player inventory slot.
     */
    public PacketTempCellAction(Action action, int tempSlotIndex, int playerSlotIndex) {
        this.action = action;
        this.tempSlotIndex = tempSlotIndex;
        this.playerSlotIndex = playerSlotIndex;
        this.toInventory = false;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = Action.values()[buf.readByte()];
        this.tempSlotIndex = buf.readInt();
        this.playerSlotIndex = buf.readInt();
        this.toInventory = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeInt(tempSlotIndex);
        buf.writeInt(playerSlotIndex);
        buf.writeBoolean(toInventory);
    }

    public static class Handler implements IMessageHandler<PacketTempCellAction, IMessage> {

        @Override
        public IMessage onMessage(PacketTempCellAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;
                if (!(container instanceof ContainerCellTerminalBase)) return;

                ContainerCellTerminalBase cellContainer = (ContainerCellTerminalBase) container;
                cellContainer.handleTempCellAction(message.action, message.tempSlotIndex,
                    message.playerSlotIndex, message.toInventory);
            });

            return null;
        }
    }
}
