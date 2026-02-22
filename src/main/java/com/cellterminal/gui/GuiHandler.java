package com.cellterminal.gui;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import appeng.api.AEApi;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.container.ContainerOpenContext;
import appeng.helpers.WirelessTerminalGuiObject;

import baubles.api.BaublesApi;

import com.cellterminal.CellTerminal;
import com.cellterminal.container.ContainerCellTerminal;
import com.cellterminal.container.ContainerWirelessCellTerminal;
import com.cellterminal.part.PartCellTerminal;


public class GuiHandler implements IGuiHandler {

    public static final int GUI_CELL_TERMINAL = 0;
    public static final int GUI_WIRELESS_CELL_TERMINAL = 1;

    public static void openCellTerminalGui(EntityPlayer player, TileEntity te, AEPartLocation side) {
        BlockPos pos = te.getPos();
        int guiId = (GUI_CELL_TERMINAL << 4) | side.ordinal();
        player.openGui(CellTerminal.instance, guiId, player.getEntityWorld(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static void openWirelessCellTerminalGui(EntityPlayer player, int slot, boolean isBauble) {
        int guiId = (GUI_WIRELESS_CELL_TERMINAL << 4);
        player.openGui(CellTerminal.instance, guiId, player.getEntityWorld(), slot, isBauble ? 1 : 0, 0);
    }

    @Nullable
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        int guiType = id >> 4;
        AEPartLocation side = AEPartLocation.fromOrdinal(id & 7);

        if (guiType == GUI_CELL_TERMINAL) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartCellTerminal) {
                    PartCellTerminal cellPart = (PartCellTerminal) part;
                    ContainerCellTerminal container = new ContainerCellTerminal(player.inventory, cellPart);
                    container.setOpenContext(new ContainerOpenContext(part));
                    container.getOpenContext().setWorld(world);
                    container.getOpenContext().setX(x);
                    container.getOpenContext().setY(y);
                    container.getOpenContext().setZ(z);
                    container.getOpenContext().setSide(side);

                    return container;
                }
            }
        } else if (guiType == GUI_WIRELESS_CELL_TERMINAL) {
            boolean isBauble = y == 1;
            ItemStack terminal = ItemStack.EMPTY;
            if (!isBauble) {
                if (x >= 0 && x < player.inventory.mainInventory.size()) {
                    terminal = player.inventory.getStackInSlot(x);
                }
            } else {
                terminal = BaublesApi.getBaublesHandler(player).getStackInSlot(x);
            }
            IWirelessTermHandler handler = AEApi
                .instance()
                .registries()
                .wireless()
                .getWirelessTerminalHandler(terminal);
            return new ContainerWirelessCellTerminal(
                player.inventory,
                new WirelessTerminalGuiObject(handler, terminal, player, world, x, y, z)
            );
        }

        return null;
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        int guiType = id >> 4;
        AEPartLocation side = AEPartLocation.fromOrdinal(id & 7);

        if (guiType == GUI_CELL_TERMINAL) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartCellTerminal) {
                    return new GuiCellTerminal(player.inventory, (PartCellTerminal) part);
                }
            }
        } else if (guiType == GUI_WIRELESS_CELL_TERMINAL) {
            boolean isBauble = y == 1;
            ItemStack terminal = ItemStack.EMPTY;
            if (!isBauble) {
                if (x >= 0 && x < player.inventory.mainInventory.size()) {
                    terminal = player.inventory.getStackInSlot(x);
                }
            } else {
                terminal = BaublesApi.getBaublesHandler(player).getStackInSlot(x);
            }
            IWirelessTermHandler handler = AEApi
                .instance()
                .registries()
                .wireless()
                .getWirelessTerminalHandler(terminal);
            return new GuiWirelessCellTerminal(
                player.inventory,
                new WirelessTerminalGuiObject(handler, terminal, player, world, x, y, z)
            );
        }

        return null;
    }
}
