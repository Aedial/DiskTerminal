package com.cellterminal.gui.networktools;

import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;


/**
 * Modal dialog for confirming network tool execution.
 */
public class GuiToolConfirmationModal {

    private static final int MIN_MODAL_WIDTH = 200;
    private static final int MAX_MODAL_WIDTH = 300;
    private static final int PADDING = 10;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 20;
    private static final int TITLE_HEIGHT = 20;

    private final INetworkTool tool;
    private final INetworkTool.ToolContext context;
    private final FontRenderer fontRenderer;
    private final int screenWidth;
    private final int screenHeight;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    // Dynamically calculated modal dimensions
    private final int modalWidth;
    private final int modalHeight;
    private final int modalX;
    private final int modalY;
    private final List<String> wrappedMessage;

    private boolean confirmHovered = false;
    private boolean cancelHovered = false;

    public GuiToolConfirmationModal(INetworkTool tool,
                                     INetworkTool.ToolContext context,
                                     FontRenderer fontRenderer,
                                     int screenWidth, int screenHeight,
                                     Runnable onConfirm, Runnable onCancel) {
        this.tool = tool;
        this.context = context;
        this.fontRenderer = fontRenderer;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        // Calculate modal dimensions based on content
        String title = I18n.format("gui.cellterminal.networktools.confirm.title", tool.getName());
        String message = tool.getConfirmationMessage(context);

        // Calculate width based on title length
        int titleWidth = fontRenderer.getStringWidth(title) + PADDING * 2;
        int contentWidth = Math.max(MIN_MODAL_WIDTH, Math.min(MAX_MODAL_WIDTH, titleWidth));

        // Wrap message text
        int maxTextWidth = contentWidth - PADDING * 2;
        this.wrappedMessage = fontRenderer.listFormattedStringToWidth(message, maxTextWidth);

        // Calculate height based on wrapped message
        int messageHeight = wrappedMessage.size() * (fontRenderer.FONT_HEIGHT + 2);
        int totalHeight = PADDING + TITLE_HEIGHT + messageHeight + PADDING + BUTTON_HEIGHT + PADDING;

        this.modalWidth = contentWidth;
        this.modalHeight = totalHeight;
        this.modalX = (screenWidth - modalWidth) / 2;
        this.modalY = (screenHeight - modalHeight) / 2;
    }

    /**
     * Draw the modal.
     */
    public void draw(int mouseX, int mouseY) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 500);  // Above other content but below tooltips

        // Reset GL state for proper color rendering
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        );
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw modal background
        Gui.drawRect(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF303030);

        // Draw modal border
        Gui.drawRect(modalX, modalY, modalX + modalWidth, modalY + 2, 0xFFFFFFFF);
        Gui.drawRect(modalX, modalY, modalX + 2, modalY + modalHeight, 0xFFFFFFFF);
        Gui.drawRect(modalX + modalWidth - 2, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF555555);
        Gui.drawRect(modalX, modalY + modalHeight - 2, modalX + modalWidth, modalY + modalHeight, 0xFF555555);

        // Draw title
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        String title = I18n.format("gui.cellterminal.networktools.confirm.title", tool.getName());
        fontRenderer.drawStringWithShadow(title, modalX + PADDING, modalY + PADDING, 0xFFFF00);

        // Draw confirmation message (wrapped)
        int textY = modalY + PADDING + TITLE_HEIGHT;

        for (String line : wrappedMessage) {
            fontRenderer.drawString(line, modalX + PADDING, textY, 0xFFFFFF);
            textY += fontRenderer.FONT_HEIGHT + 2;
        }

        // Draw buttons
        int buttonsY = modalY + modalHeight - BUTTON_HEIGHT - PADDING;
        int confirmX = modalX + (modalWidth / 2) - BUTTON_WIDTH - (BUTTON_SPACING / 2);
        int cancelX = modalX + (modalWidth / 2) + (BUTTON_SPACING / 2);

        // Check hover state
        confirmHovered = isMouseOver(mouseX, mouseY, confirmX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT);
        cancelHovered = isMouseOver(mouseX, mouseY, cancelX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT);

        // Draw Confirm button
        drawButton(confirmX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT,
            I18n.format("gui.cellterminal.networktools.confirm.do_it"),
            confirmHovered, true);

        // Draw Cancel button
        drawButton(cancelX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT,
            I18n.format("gui.cellterminal.networktools.confirm.cancel"),
            cancelHovered, false);

        // Restore GL state
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void drawButton(int x, int y, int width, int height, String text, boolean hovered, boolean isConfirm) {
        int bgColor;
        int borderTop;
        int borderLeft;
        int borderRight;
        int borderBottom;

        if (isConfirm) {
            bgColor = hovered ? 0xFF40A040 : 0xFF308030;
            borderTop = hovered ? 0xFF60C060 : 0xFF50A050;
            borderLeft = borderTop;
            borderRight = hovered ? 0xFF206020 : 0xFF105010;
            borderBottom = borderRight;
        } else {
            bgColor = hovered ? 0xFF606060 : 0xFF505050;
            borderTop = hovered ? 0xFF808080 : 0xFF707070;
            borderLeft = borderTop;
            borderRight = hovered ? 0xFF303030 : 0xFF202020;
            borderBottom = borderRight;
        }

        GlStateManager.disableTexture2D();
        Gui.drawRect(x, y, x + width, y + height, bgColor);
        Gui.drawRect(x, y, x + width, y + 1, borderTop);
        Gui.drawRect(x, y, x + 1, y + height, borderLeft);
        Gui.drawRect(x + width - 1, y, x + width, y + height, borderRight);
        Gui.drawRect(x, y + height - 1, x + width, y + height, borderBottom);
        GlStateManager.enableTexture2D();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int textX = x + (width - fontRenderer.getStringWidth(text)) / 2;
        int textY = y + (height - fontRenderer.FONT_HEIGHT) / 2;
        fontRenderer.drawStringWithShadow(text, textX, textY, 0xFFFFFF);
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * Handle mouse click.
     * @return true if the modal handled the click and should be closed
     */
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        int buttonsY = modalY + modalHeight - BUTTON_HEIGHT - PADDING;
        int confirmX = modalX + (modalWidth / 2) - BUTTON_WIDTH - (BUTTON_SPACING / 2);
        int cancelX = modalX + (modalWidth / 2) + (BUTTON_SPACING / 2);

        if (isMouseOver(mouseX, mouseY, confirmX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            onConfirm.run();

            return true;
        }

        if (isMouseOver(mouseX, mouseY, cancelX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            onCancel.run();

            return true;
        }

        // Click outside modal also cancels
        if (!isMouseOver(mouseX, mouseY, modalX, modalY, modalWidth, modalHeight)) {
            onCancel.run();

            return true;
        }

        return false;
    }

    /**
     * Handle key press.
     * @return true if the modal handled the key
     */
    public boolean handleKeyTyped(int keyCode) {
        // ESC key cancels
        if (keyCode == Keyboard.KEY_ESCAPE) {
            onCancel.run();

            return true;
        }

        // Enter key confirms
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            onConfirm.run();

            return true;
        }

        return false;
    }
}
