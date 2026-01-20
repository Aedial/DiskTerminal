package com.cellterminal.gui.handler;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import com.cellterminal.client.CellTerminalClientConfig.TerminalStyle;
import com.cellterminal.gui.tab.ITabController;
import com.cellterminal.gui.tab.TabControllerRegistry;

/**
 * Handler for rendering tabs and the controls help widget.
 * Extracted from GuiCellTerminalBase to reduce complexity.
 */
public class TabRenderingHandler {

    private TabRenderingHandler() {}

    /**
     * Context for tab rendering operations.
     */
    public static class TabRenderContext {
        public final int guiLeft;
        public final int offsetX;
        public final int offsetY;
        public final int mouseX;
        public final int mouseY;
        public final int tabCount;
        public final int tabWidth;
        public final int tabHeight;
        public final int tabYOffset;
        public final int currentTab;
        public final RenderItem itemRender;
        public final Minecraft mc;

        public TabRenderContext(int guiLeft, int offsetX, int offsetY, int mouseX, int mouseY,
                                int tabCount, int tabWidth, int tabHeight, int tabYOffset,
                                int currentTab, RenderItem itemRender, Minecraft mc) {
            this.guiLeft = guiLeft;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.tabCount = tabCount;
            this.tabWidth = tabWidth;
            this.tabHeight = tabHeight;
            this.tabYOffset = tabYOffset;
            this.currentTab = currentTab;
            this.itemRender = itemRender;
            this.mc = mc;
        }
    }

    /**
     * Callback interface for getting tab icons.
     */
    public interface TabIconProvider {
        ItemStack getTabIcon(int tab);
        ItemStack getStorageBusIcon();
        ItemStack getInventoryIcon();
        ItemStack getPartitionIcon();
    }

    /**
     * Result of rendering tabs, containing the hovered tab index.
     */
    public static class TabRenderResult {
        public final int hoveredTab;

        public TabRenderResult(int hoveredTab) {
            this.hoveredTab = hoveredTab;
        }
    }

    /**
     * Constant for storage bus inventory tab.
     */
    public static final int TAB_STORAGE_BUS_INVENTORY = 3;
    public static final int TAB_STORAGE_BUS_PARTITION = 4;

    /**
     * Draw all tabs with proper hover highlighting.
     *
     * @param ctx The rendering context
     * @param iconProvider Provider for tab icons
     * @return Result containing hovered tab index (-1 if none)
     */
    public static TabRenderResult drawTabs(TabRenderContext ctx, TabIconProvider iconProvider) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();

        int tabY = ctx.offsetY + ctx.tabYOffset;
        int hoveredTab = -1;

        for (int i = 0; i < ctx.tabCount; i++) {
            int tabX = ctx.offsetX + 4 + (i * (ctx.tabWidth + 2));
            boolean isSelected = (i == ctx.currentTab);
            boolean isHovered = ctx.mouseX >= tabX && ctx.mouseX < tabX + ctx.tabWidth
                && ctx.mouseY >= tabY && ctx.mouseY < tabY + ctx.tabHeight;

            if (isHovered) hoveredTab = i;

            // Tab background
            int bgColor = isSelected ? 0xFFC6C6C6 : (isHovered ? 0xFFA0A0A0 : 0xFF8B8B8B);
            Gui.drawRect(tabX, tabY, tabX + ctx.tabWidth, tabY + ctx.tabHeight, bgColor);

            // Tab border (3D effect)
            Gui.drawRect(tabX, tabY, tabX + ctx.tabWidth, tabY + 1, 0xFFFFFFFF);  // top
            Gui.drawRect(tabX, tabY, tabX + 1, tabY + ctx.tabHeight, 0xFFFFFFFF);  // left
            Gui.drawRect(tabX + ctx.tabWidth - 1, tabY, tabX + ctx.tabWidth, tabY + ctx.tabHeight, 0xFF555555);  // right

            // If selected, remove bottom border to connect with main GUI
            if (isSelected) {
                Gui.drawRect(tabX + 1, tabY + ctx.tabHeight - 1, tabX + ctx.tabWidth - 1, tabY + ctx.tabHeight, 0xFFC6C6C6);
            } else {
                Gui.drawRect(tabX, tabY + ctx.tabHeight - 1, tabX + ctx.tabWidth, tabY + ctx.tabHeight, 0xFF555555);  // bottom
            }

            // Draw icon (composite for storage bus tabs)
            if (i == TAB_STORAGE_BUS_INVENTORY || i == TAB_STORAGE_BUS_PARTITION) {
                drawCompositeTabIcon(ctx, tabX + 3, tabY + 3, i, iconProvider);
            } else {
                ItemStack icon = iconProvider.getTabIcon(i);
                if (!icon.isEmpty()) {
                    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderHelper.enableGUIStandardItemLighting();
                    ctx.itemRender.renderItemIntoGUI(icon, tabX + 3, tabY + 3);
                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.disableLighting();
                }
            }
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();

        return new TabRenderResult(hoveredTab);
    }

    /**
     * Draw a composite icon for storage bus tabs using diagonal cut view.
     * Shows top-left half of one icon and bottom-right half of the storage bus icon.
     */
    private static void drawCompositeTabIcon(TabRenderContext ctx, int x, int y, int tab, TabIconProvider iconProvider) {
        ItemStack topLeftIcon = (tab == TAB_STORAGE_BUS_INVENTORY) ? iconProvider.getInventoryIcon() : iconProvider.getPartitionIcon();
        ItemStack storageBusIcon = iconProvider.getStorageBusIcon();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        int scaleFactor = new ScaledResolution(ctx.mc).getScaleFactor();
        int offset = 4;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        RenderHelper.enableGUIStandardItemLighting();

        // Top-left icon
        if (!topLeftIcon.isEmpty()) {
            for (int row = 0; row < 16; row++) {
                int stripWidth = Math.max(0, 15 - row - 1);
                if (stripWidth <= 0) continue;

                int scissorX = x * scaleFactor;
                int scissorY = (ctx.mc.displayHeight) - ((y + row + 1) * scaleFactor);
                int scissorWidth = stripWidth * scaleFactor;
                int scissorHeight = 1 * scaleFactor;

                GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
                ctx.itemRender.renderItemIntoGUI(topLeftIcon, x - offset, y - offset);
            }
        }

        // Bottom-right icon (storage bus)
        if (!storageBusIcon.isEmpty()) {
            for (int row = 0; row < 16; row++) {
                int clipStart = Math.min(16, 17 - row);
                int stripWidth = 16 - clipStart;
                if (stripWidth <= 0) continue;

                int scissorX = (x + clipStart) * scaleFactor;
                int scissorY = (ctx.mc.displayHeight) - ((y + row + 1) * scaleFactor);
                int scissorWidth = stripWidth * scaleFactor;
                int scissorHeight = 1 * scaleFactor;

                GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
                ctx.itemRender.renderItemIntoGUI(storageBusIcon, x + offset, y + offset);
            }
        }
        RenderHelper.disableStandardItemLighting();

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.disableLighting();

        // Draw diagonal separator line
        GlStateManager.disableTexture2D();
        GlStateManager.color(0.3f, 0.3f, 0.3f, 1.0f);
        GL11.glLineWidth(1.5f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x + 15, y + 1);
        GL11.glVertex2f(x + 1, y + 15);
        GL11.glEnd();
        GL11.glLineWidth(1.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Context for controls help widget rendering.
     */
    public static class ControlsHelpContext {
        public final int guiLeft;
        public final int guiTop;
        public final int ySize;
        public final int currentTab;
        public final FontRenderer fontRenderer;
        public final TerminalStyle style;

        public ControlsHelpContext(int guiLeft, int guiTop, int ySize, int currentTab,
                                   FontRenderer fontRenderer, TerminalStyle style) {
            this.guiLeft = guiLeft;
            this.guiTop = guiTop;
            this.ySize = ySize;
            this.currentTab = currentTab;
            this.fontRenderer = fontRenderer;
            this.style = style;
        }
    }

    /**
     * Result of rendering controls help widget, containing the wrapped lines.
     */
    public static class ControlsHelpResult {
        public final List<String> wrappedLines;
        public final int cachedTab;

        public ControlsHelpResult(List<String> wrappedLines, int cachedTab) {
            this.wrappedLines = wrappedLines;
            this.cachedTab = cachedTab;
        }
    }

    // Constants for controls help layout
    private static final int CONTROLS_HELP_LEFT_MARGIN = 4;
    private static final int CONTROLS_HELP_RIGHT_MARGIN = 4;
    private static final int CONTROLS_HELP_PADDING = 6;
    private static final int CONTROLS_HELP_LINE_HEIGHT = 10;

    /**
     * Draw the controls help widget for the current tab.
     *
     * @param ctx The rendering context
     * @return Result containing wrapped lines and cached tab for exclusion area calculation
     */
    public static ControlsHelpResult drawControlsHelpWidget(ControlsHelpContext ctx) {
        ITabController controller = TabControllerRegistry.getController(ctx.currentTab);
        if (controller == null) return new ControlsHelpResult(new ArrayList<>(), ctx.currentTab);

        List<String> lines = controller.getHelpLines();
        if (lines.isEmpty()) return new ControlsHelpResult(new ArrayList<>(), ctx.currentTab);

        // Calculate panel width
        int panelWidth = ctx.guiLeft - CONTROLS_HELP_RIGHT_MARGIN - CONTROLS_HELP_LEFT_MARGIN;
        if (panelWidth < 60) panelWidth = 60;

        int textWidth = panelWidth - (CONTROLS_HELP_PADDING * 2);

        // Wrap all lines
        List<String> wrappedLines = new ArrayList<>();
        for (String line : lines) {
            if (line.isEmpty()) {
                wrappedLines.add("");
            } else {
                wrappedLines.addAll(ctx.fontRenderer.listFormattedStringToWidth(line, textWidth));
            }
        }

        // Calculate positions
        int panelRight = -CONTROLS_HELP_RIGHT_MARGIN;
        int panelLeft = -ctx.guiLeft + CONTROLS_HELP_LEFT_MARGIN;
        int contentHeight = wrappedLines.size() * CONTROLS_HELP_LINE_HEIGHT;
        int panelHeight = contentHeight + (CONTROLS_HELP_PADDING * 2);

        int bottomOffset = (ctx.style == TerminalStyle.TALL) ? 30 : 8;
        int panelBottom = ctx.ySize - bottomOffset;
        int panelTop = panelBottom - panelHeight;

        // Draw AE2-style panel background
        Gui.drawRect(panelLeft, panelTop, panelRight, panelBottom, 0xC0000000);

        // Border
        Gui.drawRect(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF606060);
        Gui.drawRect(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF606060);
        Gui.drawRect(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF303030);
        Gui.drawRect(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF303030);

        // Draw text
        int textX = panelLeft + CONTROLS_HELP_PADDING;
        int textY = panelTop + CONTROLS_HELP_PADDING;
        for (int i = 0; i < wrappedLines.size(); i++) {
            String line = wrappedLines.get(i);
            if (!line.isEmpty()) {
                ctx.fontRenderer.drawString(line, textX, textY + (i * CONTROLS_HELP_LINE_HEIGHT), 0xCCCCCC);
            }
        }

        return new ControlsHelpResult(wrappedLines, ctx.currentTab);
    }
}
