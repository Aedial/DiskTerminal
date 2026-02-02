package com.cellterminal.network;

import java.util.EnumMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cellterminal.client.CellFilter;
import com.cellterminal.container.ContainerCellTerminalBase;


/**
 * Packet sent from client to server to execute a network tool action.
 */
public class PacketNetworkToolAction implements IMessage {

    private String toolId;
    private Map<CellFilter, CellFilter.State> activeFilters;

    public PacketNetworkToolAction() {
        this.activeFilters = new EnumMap<>(CellFilter.class);
    }

    public PacketNetworkToolAction(String toolId, Map<CellFilter, CellFilter.State> activeFilters) {
        this.toolId = toolId;
        this.activeFilters = activeFilters;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.toolId = ByteBufUtils.readUTF8String(buf);

        int filterCount = buf.readInt();
        this.activeFilters = new EnumMap<>(CellFilter.class);

        for (int i = 0; i < filterCount; i++) {
            int filterOrdinal = buf.readByte();
            int stateOrdinal = buf.readByte();

            if (filterOrdinal >= 0 && filterOrdinal < CellFilter.values().length &&
                stateOrdinal >= 0 && stateOrdinal < CellFilter.State.values().length) {
                CellFilter filter = CellFilter.values()[filterOrdinal];
                CellFilter.State state = CellFilter.State.values()[stateOrdinal];
                activeFilters.put(filter, state);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, toolId);

        buf.writeInt(activeFilters.size());
        for (Map.Entry<CellFilter, CellFilter.State> entry : activeFilters.entrySet()) {
            buf.writeByte(entry.getKey().ordinal());
            buf.writeByte(entry.getValue().ordinal());
        }
    }

    public static class Handler implements IMessageHandler<PacketNetworkToolAction, IMessage> {

        @Override
        public IMessage onMessage(PacketNetworkToolAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase cellContainer = (ContainerCellTerminalBase) container;
                    cellContainer.handleNetworkToolAction(message.toolId, message.activeFilters);
                }
            });

            return null;
        }
    }
}
