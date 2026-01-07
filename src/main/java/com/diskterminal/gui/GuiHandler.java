package com.diskterminal.gui;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerOpenContext;

import com.diskterminal.DiskTerminal;
import com.diskterminal.container.ContainerDiskTerminal;
import com.diskterminal.container.ContainerPortableDiskTerminal;
import com.diskterminal.part.PartDiskTerminal;


public class GuiHandler implements IGuiHandler {

    public static final int GUI_DISK_TERMINAL = 0;
    public static final int GUI_PORTABLE_DISK_TERMINAL = 1;

    public static void openDiskTerminalGui(EntityPlayer player, TileEntity te, AEPartLocation side) {
        BlockPos pos = te.getPos();
        int guiId = (GUI_DISK_TERMINAL << 4) | side.ordinal();
        player.openGui(DiskTerminal.instance, guiId, player.getEntityWorld(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static void openPortableDiskTerminalGui(EntityPlayer player, int slot, boolean isBauble) {
        int guiId = (GUI_PORTABLE_DISK_TERMINAL << 4);
        player.openGui(DiskTerminal.instance, guiId, player.getEntityWorld(), slot, isBauble ? 1 : 0, 0);
    }

    @Nullable
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        int guiType = id >> 4;
        AEPartLocation side = AEPartLocation.fromOrdinal(id & 7);

        if (guiType == GUI_DISK_TERMINAL) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartDiskTerminal) {
                    PartDiskTerminal diskPart = (PartDiskTerminal) part;
                    ContainerDiskTerminal container = new ContainerDiskTerminal(player.inventory, diskPart);
                    container.setOpenContext(new ContainerOpenContext(part));
                    container.getOpenContext().setWorld(world);
                    container.getOpenContext().setX(x);
                    container.getOpenContext().setY(y);
                    container.getOpenContext().setZ(z);
                    container.getOpenContext().setSide(side);

                    return container;
                }
            }
        } else if (guiType == GUI_PORTABLE_DISK_TERMINAL) {
            boolean isBauble = y == 1;
            return new ContainerPortableDiskTerminal(player.inventory, x, isBauble);
        }

        return null;
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        int guiType = id >> 4;
        AEPartLocation side = AEPartLocation.fromOrdinal(id & 7);

        if (guiType == GUI_DISK_TERMINAL) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartDiskTerminal) {
                    return new GuiDiskTerminal(player.inventory, (PartDiskTerminal) part);
                }
            }
        } else if (guiType == GUI_PORTABLE_DISK_TERMINAL) {
            boolean isBauble = y == 1;
            return new GuiPortableDiskTerminal(player.inventory, x, isBauble);
        }

        return null;
    }
}
