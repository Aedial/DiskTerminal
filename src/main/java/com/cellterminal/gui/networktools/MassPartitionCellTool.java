package com.cellterminal.gui.networktools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;

import com.cells.api.IItemCompactingCell;

import com.cellterminal.client.CellInfo;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketNetworkToolAction;


/**
 * Tool that partitions all filtered storage cells from their current contents.
 * Clears any existing partition configuration, then sets partition slots based on cell contents.
 */
public class MassPartitionCellTool implements INetworkTool {

    public static final String TOOL_ID = "mass_partition_cell";

    private ItemStack cachedIcon = null;

    @Override
    public String getId() {
        return TOOL_ID;
    }

    @Override
    public String getName() {
        return I18n.format("gui.cellterminal.networktools.mass_partition_cell.name");
    }

    @Override
    public String getDescription() {
        return I18n.format("gui.cellterminal.networktools.mass_partition_cell.desc");
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("gui.cellterminal.networktools.mass_partition_cell.help.1"));
        lines.add("");
        lines.add(I18n.format("gui.cellterminal.networktools.mass_partition_cell.help.2"));

        return lines;
    }

    @Override
    public ItemStack getIcon() {
        if (cachedIcon == null) {
            cachedIcon = AEApi.instance().definitions().items().cell1k()
                .maybeStack(1).orElse(ItemStack.EMPTY);
        }

        return cachedIcon;
    }

    @Override
    public ToolPreviewInfo getPreview(ToolContext context) {
        List<FilteredCell> cells = getPartitionableCells(context);

        // Count cells by type for the tooltip
        Map<String, Integer> cellTypeCount = new HashMap<>();
        for (FilteredCell fc : cells) {
            String cellName = fc.getCell().getCellItem().getDisplayName();
            cellTypeCount.merge(cellName, 1, Integer::sum);
        }

        int totalCount = cells.size();
        String countText = String.valueOf(totalCount);
        int countColor = totalCount > 0 ? 0x000000 : 0x808080;

        List<String> tooltipLines = new ArrayList<>();
        if (totalCount == 0) {
            tooltipLines.add(I18n.format("gui.cellterminal.networktools.no_targets"));
        } else {
            tooltipLines.add(I18n.format("gui.cellterminal.networktools.target_breakdown"));
            for (Map.Entry<String, Integer> entry : cellTypeCount.entrySet()) {
                tooltipLines.add("  §7" + entry.getValue() + "x " + entry.getKey());
            }
        }

        return new ToolPreviewInfo(getIcon(), countText, countColor, tooltipLines);
    }

    @Override
    public String getExecutionError(ToolContext context) {
        List<FilteredCell> cells = getPartitionableCells(context);
        if (cells.isEmpty()) return I18n.format("gui.cellterminal.networktools.error.no_cells");

        return null;
    }

    @Override
    public String getConfirmationMessage(ToolContext context) {
        int count = getPartitionableCells(context).size();

        return I18n.format("gui.cellterminal.networktools.mass_partition_cell.confirm", count);
    }

    /**
     * Get filtered cells that can be partitioned, excluding compacting cells.
     */
    private List<FilteredCell> getPartitionableCells(ToolContext context) {
        List<FilteredCell> result = new ArrayList<>();

        for (FilteredCell fc : context.getFilteredCells()) {
            ItemStack cellItem = fc.getCell().getCellItem();

            // Skip compacting cells - they use a special chain mechanism
            if (cellItem.getItem() instanceof IItemCompactingCell) continue;

            result.add(fc);
        }

        return result;
    }

    @Override
    public void execute(ToolContext context) {
        CellTerminalNetwork.INSTANCE.sendToServer(
            new PacketNetworkToolAction(TOOL_ID, context.getActiveFilters()));
    }
}
