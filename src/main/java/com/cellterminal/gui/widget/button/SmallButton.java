package com.cellterminal.gui.widget.button;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.widget.AbstractWidget;


/**
 * A small 8x8 button widget rendered from a texture atlas.
 *
 * The texture is {@code textures/guis/atlas.png}, arranged as an 5x2 grid
 * of 8x8 icons. The column is determined by the {@link ButtonType} and the
 * row is determined by the hover state (0=normal, 1=hovered).
 *
 * @see ButtonType
 * @see SmallSwitchingButton
 */
public class SmallButton extends AbstractWidget {

    private static final int SIZE = GuiConstants.SMALL_BUTTON_SIZE;

    private static final ResourceLocation TEXTURE = new ResourceLocation("cellterminal", "textures/guis/atlas.png");

    protected ButtonType type;
    private final Runnable onClick;

    /**
     * @param x X position relative to GUI
     * @param y Y position relative to GUI
     * @param type The button type (determines texture column and tooltip)
     * @param onClick Callback invoked when the button is clicked
     */
    public SmallButton(int x, int y, ButtonType type, Runnable onClick) {
        super(x, y, SIZE, SIZE);
        this.type = type;
        this.onClick = onClick;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        boolean hovered = isHovered(mouseX, mouseY);

        Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();

        int texX = GuiConstants.SMALL_BUTTON_X + type.getTextureX();
        int texY = GuiConstants.SMALL_BUTTON_Y + (hovered ? SIZE : 0);
        Gui.drawScaledCustomSizeModalRect(
            this.x, this.y, texX, texY, SIZE, SIZE, SIZE, SIZE,
            GuiConstants.ATLAS_WIDTH, GuiConstants.ATLAS_HEIGHT);
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible || button != 0) return false;
        if (!isHovered(mouseX, mouseY)) return false;

        onClick.run();

        return true;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return Collections.emptyList();

        return Collections.singletonList(type.getTooltip());
    }

    /**
     * Get the current button type.
     */
    public ButtonType getType() {
        return type;
    }

    /**
     * Set the button type (changes appearance and tooltip).
     */
    public void setType(ButtonType type) {
        this.type = type;
    }
}
