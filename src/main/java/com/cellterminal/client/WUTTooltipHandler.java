package com.cellterminal.client;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cellterminal.integration.AE2WUTIntegration;
import com.cellterminal.items.ItemWirelessCellTerminal;


/**
 * Adds temp cell information to Wireless Universal Terminal item tooltips.
 * Uses Forge's ItemTooltipEvent to inject tooltip lines without requiring a mixin.
 */
@SideOnly(Side.CLIENT)
public class WUTTooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        // Skip if AE2WUT isn't loaded
        if (!AE2WUTIntegration.isModLoaded()) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // Only process WUT items
        if (!AE2WUTIntegration.isWirelessUniversalTerminal(stack)) return;

        // Check if this WUT has the Cell Terminal mode installed
        int[] modes = AE2WUTIntegration.getWUTModes(stack);
        if (modes == null) return;

        boolean hasCellTerminalMode = false;
        byte cellMode = AE2WUTIntegration.getCellTerminalMode();
        for (int mode : modes) {
            if (mode == cellMode) {
                hasCellTerminalMode = true;
                break;
            }
        }

        if (!hasCellTerminalMode) return;

        // Get temp cell count from the WUT's NBT
        int tempCellCount = ItemWirelessCellTerminal.getTempCellCount(stack);
        if (tempCellCount <= 0) return;

        // Add temp cell count to tooltip
        List<String> tooltip = event.getToolTip();
        tooltip.add(I18n.format("item.cellterminal.wireless_cell_terminal.temp_cells", tempCellCount));
    }
}
