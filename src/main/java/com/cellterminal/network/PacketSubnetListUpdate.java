package com.cellterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cellterminal.gui.GuiCellTerminalBase;


/**
 * Packet sent from server to client with subnet list data.
 * Contains the full list of subnets connected to the current network.
 */
public class PacketSubnetListUpdate implements IMessage {

    private NBTTagCompound data;

    public PacketSubnetListUpdate() {
        this.data = new NBTTagCompound();
    }

    public PacketSubnetListUpdate(NBTTagCompound data) {
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, data);
    }

    public NBTTagCompound getData() {
        return data;
    }

    public static class Handler implements IMessageHandler<PacketSubnetListUpdate, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketSubnetListUpdate message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (Minecraft.getMinecraft().currentScreen instanceof GuiCellTerminalBase) {
                    GuiCellTerminalBase gui = (GuiCellTerminalBase) Minecraft.getMinecraft().currentScreen;
                    gui.handleSubnetListUpdate(message.data);
                }
            });

            return null;
        }
    }
}
