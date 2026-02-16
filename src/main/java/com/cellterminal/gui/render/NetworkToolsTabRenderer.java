package com.cellterminal.gui.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.networktools.INetworkTool;
import com.cellterminal.gui.networktools.INetworkTool.ToolContext;
import com.cellterminal.gui.networktools.INetworkTool.ToolPreviewInfo;
import com.cellterminal.gui.networktools.NetworkToolRegistry;


/**
 * Renderer for the Network Tools tab (Tab 5).
 * Displays a scrollable list of network tools with preview information.
 */
public class NetworkToolsTabRenderer {

    private static final int TOOL_ROW_HEIGHT = 36;
    private static final int TOOL_PADDING = 4;
    private static final int ICON_SIZE = 16;
    private static final int RUN_BUTTON_SIZE = 16;
    private static final int HELP_BUTTON_SIZE = 10;
    private static final int HELP_BUTTON_Y_OFFSET = 3;

    private final FontRenderer fontRenderer;
    private final RenderItem itemRender;

    // Hover tracking
    private int hoveredToolIndex = -1;
    private boolean launchButtonHovered = false;
    private boolean helpButtonHovered = false;

    public NetworkToolsTabRenderer(FontRenderer fontRenderer, RenderItem itemRender) {
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
    }

    /**
     * Draw the Network Tools tab content.
     */
    public void draw(int currentScroll, int rowsVisible,
                     int relMouseX, int relMouseY,
                     ToolContext toolContext,
                     RenderContext ctx) {

        // Reset hover state
        hoveredToolIndex = -1;
        launchButtonHovered = false;
        helpButtonHovered = false;
        ctx.hoveredNetworkTool = null;
        ctx.hoveredNetworkToolLaunchButton = null;
        ctx.hoveredNetworkToolHelpButton = null;
        ctx.hoveredNetworkToolPreview = null;

        List<INetworkTool> tools = NetworkToolRegistry.getAllTools();
        int contentY = GuiConstants.CONTENT_START_Y;
        int contentHeight = rowsVisible * GuiConstants.ROW_HEIGHT;

        // Calculate visible tools
        int toolsPerPage = contentHeight / TOOL_ROW_HEIGHT;
        int startIndex = currentScroll;
        int endIndex = Math.min(startIndex + toolsPerPage + 1, tools.size());

        int y = contentY;
        for (int i = startIndex; i < endIndex; i++) {
            INetworkTool tool = tools.get(i);
            int toolY = y;
            int toolHeight = TOOL_ROW_HEIGHT;

            // Skip if completely outside visible area
            if (toolY + toolHeight < contentY || toolY > contentY + contentHeight) {
                y += TOOL_ROW_HEIGHT;
                continue;
            }

            drawToolRow(tool, i, toolY, relMouseX, relMouseY, toolContext, ctx);
            y += TOOL_ROW_HEIGHT;
        }
    }

    private void drawToolRow(INetworkTool tool, int index, int y,
                             int relMouseX, int relMouseY,
                             ToolContext toolContext, RenderContext ctx) {

        int x = GuiConstants.GUI_INDENT;
        int width = GuiConstants.CONTENT_RIGHT_EDGE - GuiConstants.GUI_INDENT;

        // Check if mouse is over this row
        boolean rowHovered = relMouseX >= x && relMouseX < x + width &&
                            relMouseY >= y && relMouseY < y + TOOL_ROW_HEIGHT;

        // Draw row background
        int bgColor = rowHovered ? 0x30FFFFFF : 0x20FFFFFF;
        Gui.drawRect(x, y, x + width, y + TOOL_ROW_HEIGHT - 1, bgColor);

        // Draw separator line
        Gui.drawRect(x, y + TOOL_ROW_HEIGHT - 1, x + width, y + TOOL_ROW_HEIGHT, GuiConstants.COLOR_SEPARATOR);

        // Get preview info
        ToolPreviewInfo preview = tool.getPreview(toolContext);

        // Draw help button (?) at the start of the row
        int helpButtonX = x + TOOL_PADDING;
        int helpButtonY = y + TOOL_PADDING + HELP_BUTTON_Y_OFFSET;
        boolean helpHovered = relMouseX >= helpButtonX && relMouseX < helpButtonX + HELP_BUTTON_SIZE &&
                             relMouseY >= helpButtonY && relMouseY < helpButtonY + HELP_BUTTON_SIZE;

        int helpBgColor = helpHovered ? 0xFF505050 : 0xFF606060;
        Gui.drawRect(helpButtonX, helpButtonY, helpButtonX + HELP_BUTTON_SIZE, helpButtonY + HELP_BUTTON_SIZE, helpBgColor);
        // 3D border like GuiSearchHelpButton
        Gui.drawRect(helpButtonX, helpButtonY, helpButtonX + HELP_BUTTON_SIZE, helpButtonY + 1, 0xFF808080);
        Gui.drawRect(helpButtonX, helpButtonY, helpButtonX + 1, helpButtonY + HELP_BUTTON_SIZE, 0xFF808080);
        Gui.drawRect(helpButtonX, helpButtonY + HELP_BUTTON_SIZE - 1, helpButtonX + HELP_BUTTON_SIZE, helpButtonY + HELP_BUTTON_SIZE, 0xFF303030);
        Gui.drawRect(helpButtonX + HELP_BUTTON_SIZE - 1, helpButtonY, helpButtonX + HELP_BUTTON_SIZE, helpButtonY + HELP_BUTTON_SIZE, 0xFF303030);

        String helpText = "?";
        int helpTextX = helpButtonX + (HELP_BUTTON_SIZE - fontRenderer.getStringWidth(helpText)) / 2;
        int helpTextY = helpButtonY + (HELP_BUTTON_SIZE - fontRenderer.FONT_HEIGHT) / 2 + 1;
        int helpTextColor = helpHovered ? 0xFFFF00 : 0xCCCCCC;
        fontRenderer.drawString(helpText, helpTextX, helpTextY, helpTextColor);

        // Draw tool icon
        int iconX = x + TOOL_PADDING + HELP_BUTTON_SIZE + 4;
        int iconY = y + TOOL_PADDING;
        if (!preview.getIcon().isEmpty()) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemIntoGUI(preview.getIcon(), iconX, iconY);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
        }

        // Draw preview count after icon (TODO: just delegate the rendering to preview)
        String countText = preview.getCountText();
        int countColor = preview.getCountColor();
        int countX = iconX + ICON_SIZE + 4;
        int countY = iconY + (ICON_SIZE - fontRenderer.FONT_HEIGHT) / 2;
        fontRenderer.drawString(countText, countX, countY, countColor);

        // Draw run button (square with arrow) at far right
        int runButtonX = x + width - RUN_BUTTON_SIZE - TOOL_PADDING;
        int runButtonY = y + TOOL_PADDING;

        String executionError = tool.getExecutionError(toolContext);
        boolean canExecute = executionError == null;

        boolean runHovered = relMouseX >= runButtonX && relMouseX < runButtonX + RUN_BUTTON_SIZE &&
                            relMouseY >= runButtonY && relMouseY < runButtonY + RUN_BUTTON_SIZE;

        int buttonBgColor;
        if (!canExecute) {
            buttonBgColor = 0xFF404040;
        } else if (runHovered) {
            buttonBgColor = 0xFF40A040;
        } else {
            buttonBgColor = 0xFF308030;
        }

        Gui.drawRect(runButtonX, runButtonY, runButtonX + RUN_BUTTON_SIZE, runButtonY + RUN_BUTTON_SIZE, buttonBgColor);
        // 3D button border
        Gui.drawRect(runButtonX, runButtonY, runButtonX + RUN_BUTTON_SIZE, runButtonY + 1,
            canExecute ? 0xFF60C060 : 0xFF606060);
        Gui.drawRect(runButtonX, runButtonY, runButtonX + 1, runButtonY + RUN_BUTTON_SIZE,
            canExecute ? 0xFF60C060 : 0xFF606060);
        Gui.drawRect(runButtonX + RUN_BUTTON_SIZE - 1, runButtonY, runButtonX + RUN_BUTTON_SIZE, runButtonY + RUN_BUTTON_SIZE,
            canExecute ? 0xFF206020 : 0xFF303030);
        Gui.drawRect(runButtonX, runButtonY + RUN_BUTTON_SIZE - 1, runButtonX + RUN_BUTTON_SIZE, runButtonY + RUN_BUTTON_SIZE,
            canExecute ? 0xFF206020 : 0xFF303030);

        // Draw arrow centered in the button
        int arrowTextColor = canExecute ? 0xFFFFFF : 0x808080;
        int arrowTextX = runButtonX + (RUN_BUTTON_SIZE - fontRenderer.getStringWidth("▶")) / 2;
        int arrowTextY = runButtonY + (RUN_BUTTON_SIZE - fontRenderer.FONT_HEIGHT) / 2 - 2;

        GlStateManager.pushMatrix();
        GlStateManager.scale(2.0F, 2.0F, 1.0F);
        fontRenderer.drawString("▶", arrowTextX / 2, arrowTextY / 2, arrowTextColor);
        GlStateManager.popMatrix();

        // Draw tool name with text wrapping
        int nameX = x + TOOL_PADDING;
        int nameY = y + TOOL_PADDING + ICON_SIZE + 2;
        int maxNameWidth = width - TOOL_PADDING * 2;
        String toolName = tool.getName();

        // Wrap the text if too long
        List<String> nameLines = fontRenderer.listFormattedStringToWidth(toolName, maxNameWidth);
        for (int i = 0; i < Math.min(nameLines.size(), 1); i++) {
            String line = nameLines.get(i);
            if (nameLines.size() > 1 && i == 0) {
                // Truncate with ellipsis if there's more
                while (fontRenderer.getStringWidth(line + "...") > maxNameWidth && line.length() > 0) {
                    line = line.substring(0, line.length() - 1);
                }
                line = line + "...";
            }
            fontRenderer.drawString(line, nameX, nameY, 0x000000);
        }

        // Update hover state
        if (rowHovered) {
            hoveredToolIndex = index;
            ctx.hoveredNetworkTool = tool;
            ctx.hoveredNetworkToolPreview = preview;

            if (runHovered && canExecute) {
                launchButtonHovered = true;
                ctx.hoveredNetworkToolLaunchButton = tool;
            }

            if (helpHovered) {
                helpButtonHovered = true;
                ctx.hoveredNetworkToolHelpButton = tool;
            }
        }
    }

    public int getHoveredToolIndex() {
        return hoveredToolIndex;
    }

    public boolean isLaunchButtonHovered() {
        return launchButtonHovered;
    }

    public boolean isHelpButtonHovered() {
        return helpButtonHovered;
    }

    /**
     * Get the number of logical rows for scrollbar calculation.
     * Each tool is one "row" for scrolling purposes.
     */
    public int getRowCount() {
        return NetworkToolRegistry.getToolCount();
    }
}
