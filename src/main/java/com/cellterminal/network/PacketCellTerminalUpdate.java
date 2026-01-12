package com.cellterminal.network;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cellterminal.gui.GuiCellTerminal;
import com.cellterminal.gui.GuiWirelessCellTerminal;


/**
 * Packet containing compressed NBT data for cell terminal updates.
 * Modeled after AE2's PacketCompressedNBT.
 */
public class PacketCellTerminalUpdate implements IMessage {

    private NBTTagCompound data;

    public PacketCellTerminalUpdate() {
        this.data = new NBTTagCompound();
    }

    public PacketCellTerminalUpdate(NBTTagCompound data) {
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int length = buf.readInt();
            byte[] compressed = new byte[length];
            buf.readBytes(compressed);

            try (GZIPInputStream gzis = new GZIPInputStream(new ByteBufInputStream(buf.resetReaderIndex().skipBytes(4)))) {
                this.data = CompressedStreamTools.read(new DataInputStream(gzis));
            }
        } catch (IOException e) {
            this.data = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        try {
            // Write placeholder for length
            int lengthIndex = buf.writerIndex();
            buf.writeInt(0);

            int startIndex = buf.writerIndex();

            try (GZIPOutputStream gzos = new GZIPOutputStream(new ByteBufOutputStream(buf))) {
                CompressedStreamTools.write(this.data, new DataOutputStream(gzos));
            }

            int endIndex = buf.writerIndex();
            int length = endIndex - startIndex;

            // Go back and write actual length
            buf.setInt(lengthIndex, length);
        } catch (IOException e) {
            // Error compressing data
        }
    }

    public static class Handler implements IMessageHandler<PacketCellTerminalUpdate, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketCellTerminalUpdate message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;

                if (screen instanceof GuiCellTerminal) {
                    ((GuiCellTerminal) screen).postUpdate(message.data);
                } else if (screen instanceof GuiWirelessCellTerminal) {
                    ((GuiWirelessCellTerminal) screen).postUpdate(message.data);
                }
            });

            return null;
        }
    }
}
