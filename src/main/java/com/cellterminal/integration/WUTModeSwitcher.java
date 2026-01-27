package com.cellterminal.integration;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.helpers.WirelessTerminalGuiObject;


/**
 * Manages WUT mode switching buttons for the Cell Terminal GUI.
 * Shows buttons to switch to other terminal modes when opened via WUT.
 * Positioned on the right side of the GUI, expanding vertically.
 */
@SideOnly(Side.CLIENT)
public class WUTModeSwitcher {

    private static final int BUTTON_SIZE = 16;
    private static final int BUTTON_SPACING = 1;

    private final WirelessTerminalGuiObject wirelessTerminalGuiObject;
    private final List<WUTModeButton> modeButtons = new ArrayList<>();
    private WUTModeButton toggleButton;
    private boolean expanded = false;
    private Rectangle exclusionArea = new Rectangle();
    private int baseX;
    private int baseY;
    private int maxY;  // Maximum Y before we'd go off-screen

    public WUTModeSwitcher(WirelessTerminalGuiObject wth) {
        this.wirelessTerminalGuiObject = wth;
    }

    /**
     * Initialize and add buttons to the GUI.
     * Returns the next available button ID.
     * Positions buttons on the right side of the GUI, expanding vertically.
     *
     * @param isSmallStyle true if the GUI is in small/compact style
     */
    public int initButtons(List<GuiButton> buttonList, int startButtonId, int guiLeft, int guiTop, int guiHeight, int screenHeight, boolean isSmallStyle) {
        if (!AE2WUTIntegration.isModLoaded()) return startButtonId;

        modeButtons.clear();

        int[] modes = AE2WUTIntegration.getWUTModes(wirelessTerminalGuiObject.getItemStack());
        if (modes == null || modes.length == 0) return startButtonId;

        // Position toggle button on the right side, below the title/search bar area
        this.baseX = guiLeft + 208 + 2;  // Just to the right of the main GUI
        this.baseY = guiTop + 18;  // Below the title/search bar area
        this.maxY = Math.max(baseY + 100, screenHeight - BUTTON_SIZE - 4);  // Leave margin at bottom

        // Create toggle button (shows arrows to indicate expandable)
        toggleButton = new WUTModeButton(startButtonId++, baseX, baseY, (byte) -1, true);
        buttonList.add(toggleButton);

        // Create mode buttons (hidden by default), simple vertical layout
        int buttonIndex = 0;
        int buttonsPerCol = Math.max(1, (maxY - baseY) / (BUTTON_SIZE + BUTTON_SPACING));

        for (int mode : modes) {
            // Skip the current Cell Terminal mode
            if (mode == AE2WUTIntegration.getCellTerminalMode()) continue;
            // Skip mode 0 (base terminal without functionality)
            if (mode == 0) continue;

            // Simple column layout: calculate column and row directly
            int col = buttonIndex / buttonsPerCol;
            int row = buttonIndex % buttonsPerCol;

            int x = baseX + col * (BUTTON_SIZE + BUTTON_SPACING);
            int y = baseY + (row + 1) * (BUTTON_SIZE + BUTTON_SPACING);

            WUTModeButton modeBtn = new WUTModeButton(startButtonId++, x, y, (byte) mode, false);
            modeBtn.visible = false;
            modeButtons.add(modeBtn);
            buttonList.add(modeBtn);

            buttonIndex++;
        }

        updateExclusionArea();

        return startButtonId;
    }

    /**
     * Backwards compatible version without style parameters.
     */
    public int initButtons(List<GuiButton> buttonList, int startButtonId, int guiLeft, int guiTop, int guiHeight) {
        // Default to screen height = guiTop + guiHeight + some margin
        return initButtons(buttonList, startButtonId, guiLeft, guiTop, guiHeight, guiTop + guiHeight + 50, false);
    }

    /**
     * Update the JEI exclusion area based on current button states.
     */
    private void updateExclusionArea() {
        if (toggleButton == null) {
            exclusionArea = new Rectangle();

            return;
        }

        int minX = toggleButton.x;
        int minY = toggleButton.y;
        int maxX = toggleButton.x + toggleButton.width;
        int maxY = toggleButton.y + toggleButton.height;

        if (expanded) {
            for (WUTModeButton btn : modeButtons) {
                if (btn.visible) {
                    minX = Math.min(minX, btn.x);
                    minY = Math.min(minY, btn.y);
                    maxX = Math.max(maxX, btn.x + btn.width);
                    maxY = Math.max(maxY, btn.y + btn.height);
                }
            }
        }

        exclusionArea = new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Get the JEI exclusion area for the switcher buttons.
     */
    public Rectangle getExclusionArea() {
        return exclusionArea;
    }

    /**
     * Handle button click. Returns true if the click was handled.
     */
    public boolean handleButtonClick(GuiButton button) {
        if (!AE2WUTIntegration.isModLoaded()) return false;

        if (button == toggleButton) {
            expanded = !expanded;
            for (WUTModeButton btn : modeButtons) btn.visible = expanded;
            updateExclusionArea();

            return true;
        }

        for (WUTModeButton btn : modeButtons) {
            if (button == btn) {
                AE2WUTIntegration.openWUTMode(wirelessTerminalGuiObject, btn.getMode());

                return true;
            }
        }

        return false;
    }

    /**
     * Draw tooltips for hovered buttons.
     */
    public void drawTooltips(int mouseX, int mouseY, GuiScreen gui) {
        if (!AE2WUTIntegration.isModLoaded()) return;

        if (toggleButton != null && toggleButton.isMouseOver()) {
            List<String> tooltip = new ArrayList<>();
            tooltip.add(expanded ? "Hide terminal modes" : "Switch terminal mode");
            gui.drawHoveringText(tooltip, mouseX, mouseY);

            return;
        }

        for (WUTModeButton btn : modeButtons) {
            if (btn.visible && btn.isMouseOver()) {
                ItemStack icon = AE2WUTIntegration.getWUTModeIcon(btn.getMode());
                if (!icon.isEmpty()) {
                    List<String> tooltip = new ArrayList<>();
                    tooltip.add(icon.getDisplayName());
                    gui.drawHoveringText(tooltip, mouseX, mouseY);
                }

                return;
            }
        }
    }

    /**
     * Check if mode switcher has any buttons (i.e., WUT integration is active).
     */
    public boolean hasButtons() {
        return toggleButton != null;
    }

    /**
     * Custom button for WUT mode switching.
     * Uses AE2's states.png texture for consistent styling with other terminal buttons.
     * Toggle button shows 2x scaled black arrows; mode buttons show terminal icons.
     */
    @SideOnly(Side.CLIENT)
    private static class WUTModeButton extends GuiButton {

        private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2", "textures/guis/states.png");

        private final byte mode;
        private final boolean isToggle;

        public WUTModeButton(int buttonId, int x, int y, byte mode, boolean isToggle) {
            super(buttonId, x, y, BUTTON_SIZE, BUTTON_SIZE, "");
            this.mode = mode;
            this.isToggle = isToggle;
        }

        public byte getMode() {
            return mode;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;

            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;

            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.enableBlend();

            // Draw button background with 3D border like filter buttons
            int bgColor = this.hovered ? 0xFF9E9E9E : 0xFF8B8B8B;
            drawRect(this.x, this.y, this.x + this.width, this.y + this.height, bgColor);

            // 3D border - light top/left, dark bottom/right
            int lightColor = this.hovered ? 0xFFBDBDBD : 0xFFB0B0B0;
            int darkColor = this.hovered ? 0xFF6E6E6E : 0xFF5E5E5E;
            drawRect(this.x, this.y, this.x + this.width, this.y + 1, lightColor);
            drawRect(this.x, this.y, this.x + 1, this.y + this.height, lightColor);
            drawRect(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, darkColor);
            drawRect(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, darkColor);

            if (isToggle) {
                // Draw ⇄ arrow scaled 2x in black
                GlStateManager.pushMatrix();

                float centerX = this.x + this.width / 2.0f;
                float centerY = this.y + this.height / 2.0f;

                // Scale 2x and center
                GlStateManager.translate(centerX, centerY, 0);
                GlStateManager.scale(2.0f, 2.0f, 1.0f);

                String arrow = "\u21C4";  // ⇄
                int arrowWidth = mc.fontRenderer.getStringWidth(arrow);

                // Black color (slightly softer on hover)
                int color = this.hovered ? 0x202020 : 0x000000;
                mc.fontRenderer.drawString(arrow, -arrowWidth / 2, -4, color);

                GlStateManager.popMatrix();
            } else {
                // Draw terminal icon for mode buttons
                ItemStack icon = AE2WUTIntegration.getWUTModeIcon(mode);
                if (!icon.isEmpty()) {
                    RenderHelper.enableGUIStandardItemLighting();
                    mc.getRenderItem().renderItemAndEffectIntoGUI(icon, this.x, this.y);
                    RenderHelper.disableStandardItemLighting();
                }
            }

            GlStateManager.disableBlend();
        }
    }
}
