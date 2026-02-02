package com.cellterminal.gui.networktools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;

import com.cellterminal.client.StorageBusInfo;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketNetworkToolAction;


/**
 * Tool that partitions all filtered storage buses from their current contents.
 * Clears any existing filter configuration, then sets filter slots based on connected inventory contents.
 */
public class MassPartitionBusTool implements INetworkTool {

    public static final String TOOL_ID = "mass_partition_bus";

    private ItemStack cachedIcon = null;

    @Override
    public String getId() {
        return TOOL_ID;
    }

    @Override
    public String getName() {
        return I18n.format("gui.cellterminal.networktools.mass_partition_bus.name");
    }

    @Override
    public String getDescription() {
        return I18n.format("gui.cellterminal.networktools.mass_partition_bus.desc");
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("gui.cellterminal.networktools.mass_partition_bus.help.1"));
        lines.add("");
        lines.add(I18n.format("gui.cellterminal.networktools.mass_partition_bus.help.2"));

        return lines;
    }

    @Override
    public ItemStack getIcon() {
        if (cachedIcon == null) {
            cachedIcon = AEApi.instance().definitions().parts().storageBus()
                .maybeStack(1).orElse(ItemStack.EMPTY);
        }

        return cachedIcon;
    }

    @Override
    public ToolPreviewInfo getPreview(ToolContext context) {
        List<StorageBusInfo> buses = context.getFilteredStorageBuses();

        // Count buses by type for the tooltip
        Map<String, Integer> busTypeCount = new HashMap<>();
        for (StorageBusInfo bus : buses) {
            String typeName = getBusTypeName(bus);
            busTypeCount.merge(typeName, 1, Integer::sum);
        }

        int totalCount = buses.size();
        String countText = String.valueOf(totalCount);
        int countColor = totalCount > 0 ? 0x000000 : 0x808080;

        List<String> tooltipLines = new ArrayList<>();
        if (totalCount == 0) {
            tooltipLines.add(I18n.format("gui.cellterminal.networktools.no_targets"));
        } else {
            tooltipLines.add(I18n.format("gui.cellterminal.networktools.target_breakdown"));
            for (Map.Entry<String, Integer> entry : busTypeCount.entrySet()) {
                tooltipLines.add("  §7" + entry.getValue() + "x " + entry.getKey());
            }
        }

        return new ToolPreviewInfo(getIcon(), countText, countColor, tooltipLines);
    }

    private String getBusTypeName(StorageBusInfo bus) {
        return bus.getLocalizedName();
    }

    @Override
    public String getExecutionError(ToolContext context) {
        List<StorageBusInfo> buses = context.getFilteredStorageBuses();
        if (buses.isEmpty()) return I18n.format("gui.cellterminal.networktools.error.no_buses");

        return null;
    }

    @Override
    public String getConfirmationMessage(ToolContext context) {
        int count = context.getFilteredStorageBuses().size();

        return I18n.format("gui.cellterminal.networktools.mass_partition_bus.confirm", count);
    }

    @Override
    public void execute(ToolContext context) {
        CellTerminalNetwork.INSTANCE.sendToServer(
            new PacketNetworkToolAction(TOOL_ID, context.getActiveFilters()));
    }
}
