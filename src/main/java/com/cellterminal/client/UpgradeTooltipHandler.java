package com.cellterminal.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.implementations.items.IUpgradeModule;

import com.cellterminal.gui.GuiCellTerminalBase;


/**
 * Adds insertion control hints to upgrade item tooltips.
 * This applies when viewing upgrades in inventory, JEI, or anywhere else.
 */
@SideOnly(Side.CLIENT)
public class UpgradeTooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        // Only process tooltips when the Cell Terminal GUI is open
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiCellTerminalBase)) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // Only add hints to upgrade items
        if (!(stack.getItem() instanceof IUpgradeModule)) return;

        List<String> tooltip = event.getToolTip();

        // Add a blank line and our control hints
        tooltip.add("");
        tooltip.add("§b" + I18n.format("gui.cellterminal.upgrade.tooltip_hint_click"));
        tooltip.add("§b" + I18n.format("gui.cellterminal.upgrade.tooltip_hint_shift_click"));
    }
}
