package com.cellterminal.gui.tab;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;

import com.cellterminal.client.KeyBindings;
import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.gui.handler.QuickPartitionHandler;
import com.cellterminal.gui.overlay.MessageHelper;
import com.cellterminal.integration.ThaumicEnergisticsIntegration;


/**
 * Tab controller for the Partition tab (Tab 2).
 * This tab displays cells with their partition configuration in a grid.
 * Supports JEI ghost ingredient drag-and-drop and quick partition keybinds.
 */
public class PartitionTabController implements ITabController {

    public static final int TAB_INDEX = 2;

    @Override
    public int getTabIndex() {
        return TAB_INDEX;
    }

    @Override
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        // Partition tab forces PARTITION mode for searching by partition contents
        return SearchFilterMode.PARTITION;
    }

    @Override
    public boolean showSearchModeButton() {
        // Partition tab hides the search mode button (mode is forced)
        return false;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();

        // Note about what keybinds target
        lines.add(I18n.format("gui.cellterminal.controls.keybind_targets"));

        lines.add("");  // spacing line

        // Keybind instructions
        String notSet = I18n.format("gui.cellterminal.controls.key_not_set");

        String autoKey = KeyBindings.QUICK_PARTITION_AUTO.isBound()
            ? KeyBindings.QUICK_PARTITION_AUTO.getDisplayName() : notSet;
        lines.add(I18n.format("gui.cellterminal.controls.key_auto", autoKey));

        // Auto type warning if set
        if (!autoKey.equals(notSet)) {
            lines.add(I18n.format("gui.cellterminal.controls.auto_warning"));
        }

        lines.add("");

        String itemKey = KeyBindings.QUICK_PARTITION_ITEM.isBound()
            ? KeyBindings.QUICK_PARTITION_ITEM.getDisplayName() : notSet;
        lines.add(I18n.format("gui.cellterminal.controls.key_item", itemKey));

        String fluidKey = KeyBindings.QUICK_PARTITION_FLUID.isBound()
            ? KeyBindings.QUICK_PARTITION_FLUID.getDisplayName() : notSet;
        lines.add(I18n.format("gui.cellterminal.controls.key_fluid", fluidKey));

        String essentiaKey = KeyBindings.QUICK_PARTITION_ESSENTIA.isBound()
            ? KeyBindings.QUICK_PARTITION_ESSENTIA.getDisplayName() : notSet;
        lines.add(I18n.format("gui.cellterminal.controls.key_essentia", essentiaKey));

        // Essentia cells warning if set and Thaumic Energistics not loaded
        if (!essentiaKey.equals(notSet) && !ThaumicEnergisticsIntegration.isModLoaded()) {
            lines.add(I18n.format("gui.cellterminal.controls.essentia_warning"));
        }

        lines.add("");

        lines.add(I18n.format("gui.cellterminal.controls.jei_drag"));
        lines.add(I18n.format("gui.cellterminal.controls.click_to_remove"));
        lines.add(I18n.format("gui.cellterminal.controls.double_click_storage"));
        lines.add(I18n.format("gui.cellterminal.right_click_rename"));

        return lines;
    }

    @Override
    public boolean handleClick(TabClickContext context) {
        // Handle clear-partition button click
        if (context.hoveredClearPartitionButtonCell != null && context.isLeftClick()) {
            // Clear all action is handled in GuiCellTerminalBase for now
            // as it requires sending a network packet
            return false;  // Let the GUI handle this
        }

        return false;
    }

    @Override
    public boolean handleKeyTyped(int keyCode, TabContext context) {
        KeyBindings matchedKey = null;
        QuickPartitionHandler.PartitionType type = null;

        if (KeyBindings.QUICK_PARTITION_AUTO.isActiveAndMatches(keyCode)) {
            matchedKey = KeyBindings.QUICK_PARTITION_AUTO;
            type = QuickPartitionHandler.PartitionType.AUTO;
        } else if (KeyBindings.QUICK_PARTITION_ITEM.isActiveAndMatches(keyCode)) {
            matchedKey = KeyBindings.QUICK_PARTITION_ITEM;
            type = QuickPartitionHandler.PartitionType.ITEM;
        } else if (KeyBindings.QUICK_PARTITION_FLUID.isActiveAndMatches(keyCode)) {
            matchedKey = KeyBindings.QUICK_PARTITION_FLUID;
            type = QuickPartitionHandler.PartitionType.FLUID;
        } else if (KeyBindings.QUICK_PARTITION_ESSENTIA.isActiveAndMatches(keyCode)) {
            matchedKey = KeyBindings.QUICK_PARTITION_ESSENTIA;
            type = QuickPartitionHandler.PartitionType.ESSENTIA;
        }

        if (matchedKey == null || type == null) return false;

        QuickPartitionHandler.QuickPartitionResult result = QuickPartitionHandler.attemptQuickPartition(
            type, context.getPartitionLines(), context.getStorageMap());

        // Display result message with appropriate color
        if (result.success) {
            MessageHelper.successRaw(result.message);
        } else {
            MessageHelper.errorRaw(result.message);
        }

        // Scroll to the cell if successful
        if (result.success && result.scrollToLine >= 0) context.scrollToLine(result.scrollToLine);

        return true;
    }

    @Override
    public boolean requiresServerPolling() {
        return false;
    }

    @Override
    public List<Object> getLines(TabContext context) {
        return context.getPartitionLines();
    }

    @Override
    public int getLineCount(TabContext context) {
        return context.getPartitionLines().size();
    }
}
