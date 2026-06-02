package com.cellterminal.client;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.implementations.items.IUpgradeModule;

import com.cellterminal.config.CellTerminalClientConfig;
import com.cellterminal.gui.GuiCellTerminalBase;
import com.cellterminal.gui.GuiConstants;


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

        // Only add hints to real upgrade items, not storage components that
        // also implement IUpgradeModule but return null from getType()
        if (!(stack.getItem() instanceof IUpgradeModule)) return;
        if (((IUpgradeModule) stack.getItem()).getType(stack) == null) return;

        List<String> tooltip = event.getToolTip();

        // Add a blank line and our control hints
        tooltip.add("");
        tooltip.add("§b" + I18n.format("gui.cellterminal.upgrade.tooltip_hint_click"));
        tooltip.add("§b" + I18n.format("gui.cellterminal.upgrade.tooltip_hint_shift_click"));

        List<String> availableEntries;

        int tab = CellTerminalClientConfig.getInstance().getSelectedTab();
        if (tab == GuiConstants.TAB_TERMINAL) {
            availableEntries = Arrays.asList(
                "§a" + I18n.format("gui.cellterminal.upgrade.tooltip_hint_entry_drive"),
                "§a" + I18n.format("gui.cellterminal.upgrade.tooltip_hint_entry_cell_lines")
            );
        } else if (tab == GuiConstants.TAB_INVENTORY || tab == GuiConstants.TAB_PARTITION) {
            availableEntries = Arrays.asList(
                "§a" + I18n.format("gui.cellterminal.upgrade.tooltip_hint_entry_drive"),
                "§a" + I18n.format("gui.cellterminal.upgrade.tooltip_hint_entry_cells")
            );
        } else if (tab == GuiConstants.TAB_TEMP_AREA) {
            availableEntries = Arrays.asList(
                "§a" + I18n.format("gui.cellterminal.upgrade.tooltip_hint_entry_cells")
            );
        } else if (tab == GuiConstants.TAB_STORAGE_BUS_INVENTORY || tab == GuiConstants.TAB_STORAGE_BUS_PARTITION) {
            availableEntries = Arrays.asList(
                "§a" + I18n.format("gui.cellterminal.upgrade.tooltip_hint_entry_storage_bus")
            );
        } else {
            availableEntries = Arrays.asList();
        }

        if (!availableEntries.isEmpty()) {
            tooltip.add("");
            tooltip.add("§a" + I18n.format("gui.cellterminal.upgrade.tooltip_hint_available_entries"));
            tooltip.addAll(availableEntries);
        }
    }
}
