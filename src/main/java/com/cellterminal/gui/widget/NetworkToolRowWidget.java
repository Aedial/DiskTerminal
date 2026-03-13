package com.cellterminal.gui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;

import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.networktools.INetworkTool;
import com.cellterminal.gui.networktools.INetworkTool.ToolContext;
import com.cellterminal.gui.networktools.INetworkTool.ToolPreviewInfo;


/**
 * Self-contained widget representing a single network tool row.
 * <p>
 * Layout (two-line row with separator at bottom):
 * <pre>
 *   [?] [icon] countText                    [▶]   <- Line 1
 *     Tool Name                                    <- Line 2
 *   ____________________________________________   <- Separator
 * </pre>
 *
 * Each row handles its own:
 * <ul>
 *   <li>Help pseudo-button (?) with tooltip</li>
 *   <li>Tool icon (ItemStack) with count text</li>
 *   <li>Run button (atlas texture, 3 states: normal/hovered/disabled)</li>
 *   <li>Tool name on the second line</li>
 *   <li>Hover detection for sub-areas (help, run, row)</li>
 *   <li>Click handling for the run button</li>
 * </ul>
 */
public class NetworkToolRowWidget extends AbstractWidget {

    public static final int ROW_HEIGHT = 36;

    private static final int PADDING = 4;
    private static final int ICON_SIZE = 16;
    private static final int HELP_BUTTON_SIZE = GuiConstants.SEARCH_HELP_TOOLTIP_BUTTON_SIZE;
    private static final int HELP_BUTTON_Y_OFFSET = 3;
    private static final int RUN_SIZE = GuiConstants.NETWORK_TOOL_RUN_BUTTON_SIZE;

    private final INetworkTool tool;
    private final FontRenderer fontRenderer;
    private final RenderItem itemRender;

    /** Callback for when the run button is clicked */
    private Runnable onRunClicked;

    /** Supplier for the tool context (re-evaluated each frame for live preview) */
    private Supplier<ToolContext> contextSupplier;

    // Sub-area positions (computed during draw, used for hover/click)
    private int helpBtnX, helpBtnY;
    private int runBtnX, runBtnY;
    private boolean runHovered, helpHovered;
    private boolean canExecute;

    public NetworkToolRowWidget(INetworkTool tool, int y,
                                FontRenderer fontRenderer, RenderItem itemRender) {
        super(GuiConstants.GUI_INDENT, y,
            GuiConstants.CONTENT_RIGHT_EDGE - GuiConstants.GUI_INDENT, ROW_HEIGHT);
        this.tool = tool;
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
    }

    public void setOnRunClicked(Runnable onRunClicked) {
        this.onRunClicked = onRunClicked;
    }

    public void setContextSupplier(java.util.function.Supplier<ToolContext> contextSupplier) {
        this.contextSupplier = contextSupplier;
    }

    public INetworkTool getTool() {
        return tool;
    }

    // ---- Drawing ----

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        boolean rowHovered = isHovered(mouseX, mouseY);

        // Row background
        int bgColor = rowHovered ? 0x30FFFFFF : 0x20FFFFFF;
        Gui.drawRect(x, y, x + width, y + height - 1, bgColor);

        // Bottom separator line
        Gui.drawRect(x, y + height - 1, x + width, y + height, GuiConstants.COLOR_SEPARATOR);

        // Get preview from current tool context
        ToolContext ctx = contextSupplier != null ? contextSupplier.get() : null;
        ToolPreviewInfo preview = ctx != null ? tool.getPreview(ctx) : null;
        String executionError = ctx != null ? tool.getExecutionError(ctx) : "No context";
        canExecute = executionError == null;

        // Compute sub-area positions
        helpBtnX = x + PADDING;
        helpBtnY = y + PADDING + HELP_BUTTON_Y_OFFSET;
        runBtnX = x + width - RUN_SIZE - PADDING;
        runBtnY = y + PADDING;

        // Check sub-area hover
        helpHovered = mouseX >= helpBtnX && mouseX < helpBtnX + HELP_BUTTON_SIZE
            && mouseY >= helpBtnY && mouseY < helpBtnY + HELP_BUTTON_SIZE;
        runHovered = mouseX >= runBtnX && mouseX < runBtnX + RUN_SIZE
            && mouseY >= runBtnY && mouseY < runBtnY + RUN_SIZE;

        drawHelpButton();
        drawToolIcon(preview);
        drawRunButton();
        drawToolName();
    }

    private void drawHelpButton() {
        // Background from atlas (same texture as GuiSearchHelpButton)
        int texX = GuiConstants.SEARCH_HELP_TOOLTIP_BUTTON_X;
        int texY = GuiConstants.SEARCH_HELP_TOOLTIP_BUTTON_Y + (helpHovered ? HELP_BUTTON_SIZE : 0);
        GuiConstants.drawAtlasSprite(
            helpBtnX, helpBtnY, texX, texY, HELP_BUTTON_SIZE, HELP_BUTTON_SIZE);
    }

    private void drawToolIcon(ToolPreviewInfo preview) {
        int iconX = x + PADDING + HELP_BUTTON_SIZE + 4;
        int iconY = y + PADDING;

        // Draw the tool icon ItemStack
        if (preview != null && !preview.getIcon().isEmpty()) {
            AbstractWidget.renderItemStack(itemRender, preview.getIcon(), iconX, iconY);
        }

        // Draw preview count text after the icon
        if (preview != null) {
            String countText = preview.getCountText();
            int countColor = preview.getCountColor();
            int countX = iconX + ICON_SIZE + 4;
            int countY = iconY + (ICON_SIZE - fontRenderer.FONT_HEIGHT) / 2;
            fontRenderer.drawString(countText, countX, countY, countColor);
        }
    }

    private void drawRunButton() {
        int texX = GuiConstants.NETWORK_TOOL_RUN_BUTTON_X;
        int texY;
        if (!canExecute) {
            texY = GuiConstants.NETWORK_TOOL_RUN_BUTTON_Y + 2 * RUN_SIZE;  // Disabled state
        } else if (runHovered) {
            texY = GuiConstants.NETWORK_TOOL_RUN_BUTTON_Y + RUN_SIZE;  // Hovered state
        } else {
            texY = GuiConstants.NETWORK_TOOL_RUN_BUTTON_Y;  // Normal state
        }

        GuiConstants.drawAtlasSprite(
            runBtnX, runBtnY, texX, texY, RUN_SIZE, RUN_SIZE);
    }

    private void drawToolName() {
        int nameX = x + PADDING;
        int nameY = y + PADDING + ICON_SIZE + 2;
        int maxNameWidth = width - PADDING * 2;
        String toolName = tool.getName();

        String displayName = AbstractWidget.trimTextToWidth(fontRenderer, toolName, maxNameWidth);
        fontRenderer.drawString(displayName, nameX, nameY, 0x000000);
    }

    // ---- Click handling ----

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible || button != 0) return false;
        if (!isHovered(mouseX, mouseY)) return false;

        // Only the run button is clickable
        if (runHovered && canExecute && onRunClicked != null) {
            onRunClicked.run();

            return true;
        }

        return false;
    }

    // ---- Tooltips ----

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return Collections.emptyList();

        // Help button tooltip: tool name + description
        if (helpHovered) {
            List<String> tooltip = new ArrayList<>();
            tooltip.add("§e" + tool.getName());
            tooltip.add("");
            for (String line : tool.getHelpLines()) tooltip.add("§7" + line);

            return tooltip;
        }

        // Row hover tooltip: tool name + preview details
        ToolContext ctx = contextSupplier != null ? contextSupplier.get() : null;
        ToolPreviewInfo preview = ctx != null ? tool.getPreview(ctx) : null;
        if (preview != null) {
            List<String> tooltipLines = preview.getTooltipLines();
            if (tooltipLines != null && !tooltipLines.isEmpty()) {
                List<String> lines = new ArrayList<>();
                lines.add("§e" + tool.getName());
                lines.add("");
                lines.addAll(tooltipLines);

                return lines;
            }
        }

        return Collections.emptyList();
    }
}
