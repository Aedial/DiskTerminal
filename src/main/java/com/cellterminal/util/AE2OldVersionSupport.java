package com.cellterminal.util;

import appeng.helpers.WirelessTerminalGuiObject;
import baubles.api.BaublesApi;
import com.cellterminal.ItemRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

public class AE2OldVersionSupport {
    public static boolean wirelessTerminalGuiObjectIsBauble(WirelessTerminalGuiObject wth, InventoryPlayer ip) {
        try {
            return wth.isBaubleSlot();
        } catch (NoSuchMethodError e) {
            return ip.getStackInSlot(wth.getInventorySlot()).getItem() != ItemRegistry.WIRELESS_CELL_TERMINAL && getBaubleStack(ip.player, wth.getInventorySlot()).getItem() == ItemRegistry.WIRELESS_CELL_TERMINAL;
        }
    }

    @Optional.Method(modid = "baubles")
    private static ItemStack getBaubleStack(EntityPlayer player, int baubleSlot) {
        return BaublesApi.getBaublesHandler(player).getStackInSlot(baubleSlot);
    }
}
