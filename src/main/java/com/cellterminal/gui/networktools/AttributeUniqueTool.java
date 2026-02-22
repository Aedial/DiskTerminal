package com.cellterminal.gui.networktools;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;

import com.cells.api.IItemCompactingCell;

import com.cellterminal.client.CellInfo;
import com.cellterminal.gui.networktools.INetworkTool.FilteredCell;
import com.cellterminal.network.CellTerminalNetwork;
import com.cellterminal.network.PacketNetworkToolAction;


/**
 * Tool that distributes content uniquely to cells.
 * Takes all unique content from filtered cells and distributes them so that each cell
 * contains exactly one content type, then partitions the cells accordingly.
 *
 * This tool requires enough available cells to hold all unique content types.
 * "Available cells" = filtered cells + any empty, non-partitioned cells of matching type.
 *
 * IMPORTANT: Item, Fluid, and Essentia cells are tracked separately and never mixed.
 * Each type needs enough cells of that same type to hold its unique content.
 */
public class AttributeUniqueTool implements INetworkTool {

    public static final String TOOL_ID = "attribute_unique";

    private ItemStack cachedIcon = null;

    /**
     * Cell type for tracking separate counts per type.
     * Mirrors the server-side CellType in NetworkToolActionHandler.
     */
    private enum CellType {
        ITEM, FLUID, ESSENTIA
    }

    /**
     * Statistics per cell type.
     */
    private static class TypeStats {
        final Set<String> uniqueKeys = new HashSet<>();
        int filteredCellCount = 0;
        int emptyCellCount = 0;

        int getUniqueCount() {
            return uniqueKeys.size();
        }

        int getTotalAvailable() {
            return filteredCellCount + emptyCellCount;
        }

        boolean hasContent() {
            return !uniqueKeys.isEmpty();
        }

        boolean hasError() {
            return hasContent() && getTotalAvailable() < getUniqueCount();
        }
    }

    @Override
    public String getId() {
        return TOOL_ID;
    }

    @Override
    public String getName() {
        return I18n.format("gui.cellterminal.networktools.attribute_unique.name");
    }

    @Override
    public String getDescription() {
        return I18n.format("gui.cellterminal.networktools.attribute_unique.desc");
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("gui.cellterminal.networktools.attribute_unique.help.1"));
        lines.add(I18n.format("gui.cellterminal.networktools.attribute_unique.help.2"));
        lines.add(I18n.format("gui.cellterminal.networktools.attribute_unique.help.3"));
        lines.add("");
        lines.add(I18n.format("gui.cellterminal.networktools.attribute_unique.help.4"));

        return lines;
    }

    @Override
    public ItemStack getIcon() {
        if (cachedIcon == null) {
            cachedIcon = AEApi.instance().definitions().blocks().drive()
                .maybeStack(1).orElse(ItemStack.EMPTY);
        }

        return cachedIcon;
    }

    /**
     * Collect statistics per cell type from the context.
     */
    private Map<CellType, TypeStats> collectTypeStats(ToolContext context) {
        Map<CellType, TypeStats> statsByType = new EnumMap<>(CellType.class);
        for (CellType type : CellType.values()) statsByType.put(type, new TypeStats());

        // First pass: collect unique items from filtered cells and track which cells are filtered
        List<FilteredCell> filteredCells = context.getFilteredCells();
        Set<CellInfo> filteredCellSet = new HashSet<>();

        for (FilteredCell fc : filteredCells) {
            CellInfo cell = fc.getCell();

            // Skip compacting cells - they use a special chain mechanism
            if (cell.getCellItem().getItem() instanceof IItemCompactingCell) continue;

            filteredCellSet.add(cell);
            CellType type = getCellType(cell);
            TypeStats stats = statsByType.get(type);

            stats.filteredCellCount++;

            for (ItemStack stack : cell.getContents()) {
                if (!stack.isEmpty()) stats.uniqueKeys.add(getItemKey(stack, type));
            }
        }

        // Second pass: count empty non-partitioned cells per type
        // Only for types that have content, and exclude cells already counted in first pass
        for (CellType type : CellType.values()) {
            TypeStats stats = statsByType.get(type);
            if (!stats.hasContent()) continue;

            for (FilteredCell fc : context.getEmptyNonPartitionedCells(type == CellType.ITEM,
                                                                        type == CellType.FLUID,
                                                                        type == CellType.ESSENTIA)) {
                CellInfo cell = fc.getCell();

                // Skip compacting cells - they use a special chain mechanism
                if (cell.getCellItem().getItem() instanceof IItemCompactingCell) continue;

                if (getCellType(cell) == type && !filteredCellSet.contains(cell)) {
                    stats.emptyCellCount++;
                }
            }
        }

        return statsByType;
    }

    private CellType getCellType(CellInfo cell) {
        if (cell.isEssentia()) return CellType.ESSENTIA;
        if (cell.isFluid()) return CellType.FLUID;

        return CellType.ITEM;
    }

    private String getItemKey(ItemStack stack, CellType type) {
        if (type == CellType.FLUID) {
            // For fluids, use a simpler key based on the representation item
            return "fluid:" + stack.getItem().getRegistryName().toString() + "@" + stack.getMetadata();
        }

        String base = stack.getItem().getRegistryName().toString() + "@" + stack.getMetadata();
        if (stack.hasTagCompound()) base += "#" + stack.getTagCompound().hashCode();

        return base;
    }

    @Override
    public ToolPreviewInfo getPreview(ToolContext context) {
        Map<CellType, TypeStats> statsByType = collectTypeStats(context);

        // Calculate totals across all types
        int totalUnique = 0;
        int totalAvailable = 0;
        boolean hasAnyError = false;

        for (TypeStats stats : statsByType.values()) {
            if (stats.hasContent()) {
                totalUnique += stats.getUniqueCount();
                totalAvailable += stats.getTotalAvailable();
                if (stats.hasError()) hasAnyError = true;
            }
        }

        String countText = totalAvailable + " / " + totalUnique;
        int countColor = hasAnyError ? 0xFF5555 : 0x000000;

        List<String> tooltipLines = new ArrayList<>();
        tooltipLines.add(I18n.format("gui.cellterminal.networktools.attribute_unique.preview.title"));
        tooltipLines.add("");

        // Show per-type breakdown
        for (CellType type : CellType.values()) {
            TypeStats stats = statsByType.get(type);
            if (!stats.hasContent()) continue;

            String typeName = type.name().toLowerCase();
            String typeLabel = I18n.format("gui.cellterminal.networktools.attribute_unique.preview.type." + typeName);
            String statusColor = stats.hasError() ? "§c" : "§a";

            tooltipLines.add(String.format("%s: %s%d / %d",
                typeLabel,
                statusColor,
                stats.getTotalAvailable(),
                stats.getUniqueCount()));
        }

        if (hasAnyError) {
            tooltipLines.add("");
            tooltipLines.add("§c" + I18n.format("gui.cellterminal.networktools.attribute_unique.error.not_enough_cells"));
        }

        return new ToolPreviewInfo(getIcon(), countText, countColor, tooltipLines);
    }

    @Override
    public String getExecutionError(ToolContext context) {
        List<FilteredCell> filteredCells = context.getFilteredCells();
        if (filteredCells.isEmpty()) return I18n.format("gui.cellterminal.networktools.error.no_cells");

        Map<CellType, TypeStats> statsByType = collectTypeStats(context);

        // Check if any content exists
        boolean hasAnyContent = false;
        for (TypeStats stats : statsByType.values()) {
            if (stats.hasContent()) {
                hasAnyContent = true;
                break;
            }
        }

        if (!hasAnyContent) return I18n.format("gui.cellterminal.networktools.error.no_items");

        // Check each type has enough cells
        StringBuilder errorBuilder = new StringBuilder();
        boolean hasError = false;

        for (CellType type : CellType.values()) {
            TypeStats stats = statsByType.get(type);
            if (!stats.hasContent()) continue;

            if (stats.hasError()) {
                hasError = true;
                String typeName = type.name().toLowerCase();
                String typeLabel = I18n.format("gui.cellterminal.networktools.attribute_unique.preview.type." + typeName);
                errorBuilder.append("\n" + I18n.format("gui.cellterminal.networktools.attribute_unique.error.type_detail",
                    typeLabel, stats.getTotalAvailable(), stats.getUniqueCount()));
            }
        }

        if (hasError) {
            return I18n.format("gui.cellterminal.networktools.attribute_unique.error.not_enough_cells_by_type")
                + errorBuilder.toString();
        }

        return null;
    }

    @Override
    public String getConfirmationMessage(ToolContext context) {
        Map<CellType, TypeStats> statsByType = collectTypeStats(context);

        int totalUnique = 0;
        int totalCells = 0;

        for (TypeStats stats : statsByType.values()) {
            if (stats.hasContent()) {
                totalUnique += stats.getUniqueCount();
                totalCells += stats.getTotalAvailable();
            }
        }

        return I18n.format("gui.cellterminal.networktools.attribute_unique.confirm",
            totalUnique, totalCells);
    }

    @Override
    public void execute(ToolContext context) {
        CellTerminalNetwork.INSTANCE.sendToServer(
            new PacketNetworkToolAction(TOOL_ID, context.getActiveFilters()));
    }
}
