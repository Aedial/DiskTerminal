package com.cellterminal.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import com.cellterminal.client.SearchFilterMode;
import com.cellterminal.gui.GuiConstants;


/**
 * Button that cycles through search filter modes and displays a visual indicator.
*/
public class GuiSearchModeButton extends GuiButton {

    private static final int SIZE = GuiConstants.SEARCH_MODE_BUTTON_SIZE;
    private static final ResourceLocation TEXTURE = new ResourceLocation(
        "cellterminal", "textures/guis/atlas.png");

    private SearchFilterMode currentMode;

    public GuiSearchModeButton(int buttonId, int x, int y, SearchFilterMode initialMode) {
        super(buttonId, x, y, SIZE, SIZE, "");
        this.currentMode = initialMode;
    }

    public void setMode(SearchFilterMode mode) {
        this.currentMode = mode;
    }

    public SearchFilterMode getMode() {
        return currentMode;
    }

    public SearchFilterMode cycleMode() {
        this.currentMode = this.currentMode.next();

        return this.currentMode;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        mc.getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();

        int texX = GuiConstants.SEARCH_MODE_BUTTON_X + currentMode.ordinal() * SIZE;
        int texY = GuiConstants.SEARCH_MODE_BUTTON_Y + (this.hovered ? SIZE : 0);
        Gui.drawScaledCustomSizeModalRect(
            this.x, this.y, texX, texY, SIZE, SIZE, SIZE, SIZE,
            GuiConstants.ATLAS_WIDTH, GuiConstants.ATLAS_HEIGHT);
    }

    /**
     * Get the tooltip lines for this button.
     */
    public List<String> getTooltip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.cellterminal.search_mode"));

        String modeKey;
        switch (currentMode) {
            case INVENTORY:
                modeKey = "gui.cellterminal.search_mode.inventory";
                break;
            case PARTITION:
                modeKey = "gui.cellterminal.search_mode.partition";
                break;
            case MIXED:
            default:
                modeKey = "gui.cellterminal.search_mode.mixed";
                break;
        }
        tooltip.add("§7" + I18n.format(modeKey));

        return tooltip;
    }
}
