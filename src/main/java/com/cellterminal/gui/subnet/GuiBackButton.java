package com.cellterminal.gui.subnet;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import com.cellterminal.gui.GuiConstants;


/**
 * A small button used to navigate to/from subnet overview mode.
 * When in normal view (main or subnet), shows a left arrow to enter overview.
 * When in overview mode, shows a right arrow to go back.
 *
 * <p>The texture is a 32x32 sprite sheet organized as a 2x2 grid of 16x16 icons:
 * <ul>
 *   <li>Row 0: normal state</li>
 *   <li>Row 1: hovered state</li>
 *   <li>Column 0: left arrow (enter overview)</li>
 *   <li>Column 1: right arrow (return from overview)</li>
 * </ul>
 */
public class GuiBackButton extends GuiButton {

    private static final int SIZE = GuiConstants.SUBNET_BUTTON_SIZE;
    private static final ResourceLocation TEXTURE = new ResourceLocation(
        "cellterminal", "textures/guis/atlas.png");

    private boolean isInOverviewMode;

    public GuiBackButton(int buttonId, int x, int y) {
        super(buttonId, x, y, SIZE, SIZE, "");
        this.isInOverviewMode = false;
    }

    /**
     * Update the button state based on whether we're in subnet overview mode.
     */
    public void setInOverviewMode(boolean inOverview) {
        this.isInOverviewMode = inOverview;
    }

    public boolean isInOverviewMode() {
        return isInOverviewMode;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        mc.getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();

        int texX = GuiConstants.SUBNET_BUTTON_X + (isInOverviewMode ? SIZE : 0);
        int texY = GuiConstants.SUBNET_BUTTON_Y + (this.hovered ? SIZE : 0);
        Gui.drawScaledCustomSizeModalRect(
            this.x, this.y, texX, texY, SIZE, SIZE, SIZE, SIZE,
            GuiConstants.ATLAS_WIDTH, GuiConstants.ATLAS_HEIGHT);
    }

    /**
     * Get the tooltip for this button.
     */
    public List<String> getTooltip() {
        List<String> tooltip = new ArrayList<>();

        if (isInOverviewMode) {
            tooltip.add(I18n.format("cellterminal.subnet.back"));
        } else {
            tooltip.add(I18n.format("cellterminal.subnet.overview"));
        }

        return tooltip;
    }
}
