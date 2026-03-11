package com.cellterminal.gui.widget.header;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;

import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.widget.button.ButtonType;
import com.cellterminal.gui.widget.button.SmallButton;


/**
 * Storage bus header widget for the Cell Terminal (tabs 4-5).
 * <p>
 * Extends the storage header with:
 * <ul>
 *   <li>IO mode button (texture-based, cycles through Read/Write/ReadWrite)</li>
 *   <li>Upgrade card icons (rendered at the left edge, inherited from AbstractHeader)</li>
 *   <li>Selection support for batch operations (quick-add via keybind)</li>
 * </ul>
 *
 * The IO mode button uses textured icons to indicate the current access mode:
 * <ul>
 *   <li>{@link ButtonType#READ_ONLY} = Read-only (extract)</li>
 *   <li>{@link ButtonType#WRITE_ONLY} = Write-only (insert)</li>
 *   <li>{@link ButtonType#READ_WRITE} = Read+Write (bidirectional split)</li>
 * </ul>
 *
 * @see StorageHeader
 * @see AbstractHeader
 */
public class StorageBusHeader extends StorageHeader {

    /** Supplier for the access restriction mode (0=NONE, 1=READ, 2=WRITE, 3=READ_WRITE) */
    private Supplier<Integer> accessModeSupplier;

    /** Whether IO mode switching is supported */
    private Supplier<Boolean> supportsIOModeSupplier;

    /** Callback when the IO mode button is clicked */
    private Runnable onIOModeClick;

    /** Textured IO mode button (READ_ONLY, WRITE_ONLY, or READ_WRITE) */
    private final SmallButton ioModeButton;

    // IO mode button hover state
    private boolean ioModeHovered = false;

    public StorageBusHeader(int y, FontRenderer fontRenderer, RenderItem itemRender) {
        super(y, fontRenderer, itemRender);
        // IO mode button: type is updated each frame from accessModeSupplier.
        // Default to READ_WRITE since it will be overwritten before drawing.
        this.ioModeButton = new SmallButton(
            GuiConstants.BUTTON_IO_MODE_X, y, ButtonType.READ_WRITE,
            () -> { if (onIOModeClick != null) onIOModeClick.run(); });
    }

    // ---- Configuration ----

    public void setAccessModeSupplier(Supplier<Integer> supplier) {
        this.accessModeSupplier = supplier;
    }

    public void setSupportsIOModeSupplier(Supplier<Boolean> supplier) {
        this.supportsIOModeSupplier = supplier;
    }

    public void setOnIOModeClick(Runnable callback) {
        this.onIOModeClick = callback;
    }

    /**
     * Whether the IO mode button is currently hovered.
     */
    public boolean isIOModeHovered() {
        return ioModeHovered;
    }

    // ---- Rendering ----

    @Override
    protected int drawHeaderContent(int mouseX, int mouseY) {
        ioModeHovered = false;

        // Draw location text
        drawLocation();

        // Draw expand/collapse indicator
        drawExpandIcon(mouseX, mouseY);

        // Draw IO mode button
        drawIOModeButton(mouseX, mouseY);

        // Draw upgrade cards (from AbstractHeader)
        if (cardsDisplay != null) cardsDisplay.draw(mouseX, mouseY);

        // Return the hover right bound (up to IO mode button area)
        return GuiConstants.BUTTON_IO_MODE_X;
    }

    /**
     * Draw the IO mode button using textured SmallButton.
     * Updates the button type each frame based on the current access restriction.
     */
    private void drawIOModeButton(int mouseX, int mouseY) {
        boolean supportsIOMode = supportsIOModeSupplier != null && supportsIOModeSupplier.get();
        if (!supportsIOMode) return;

        int accessMode = accessModeSupplier != null ? accessModeSupplier.get() : 3;

        // Map access restriction to button type
        switch (accessMode) {
            case 1:
                ioModeButton.setType(ButtonType.READ_ONLY);
                break;
            case 2:
                ioModeButton.setType(ButtonType.WRITE_ONLY);
                break;
            default:
                ioModeButton.setType(ButtonType.READ_WRITE);
                break;
        }

        // Position at current header Y (since header Y can change per frame)
        ioModeButton.setPosition(GuiConstants.BUTTON_IO_MODE_X, y);
        ioModeButton.draw(mouseX, mouseY);
        ioModeHovered = ioModeButton.isHovered(mouseX, mouseY);
    }

    // ---- Click handling ----

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) return false;

        // Left-click only for IO mode and expand/collapse
        if (button == 0) {
            // IO mode button click (delegated to SmallButton)
            boolean supportsIOMode = supportsIOModeSupplier != null && supportsIOModeSupplier.get();
            if (supportsIOMode && ioModeButton.handleClick(mouseX, mouseY, button)) {
                return true;
            }

            // Expand/collapse click
            if (expandHovered && onExpandToggle != null) {
                onExpandToggle.run();
                return true;
            }
        }

        // Name click (right-click), cards click, and header selection for quick-add (from base)
        return super.handleClick(mouseX, mouseY, button);
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return Collections.emptyList();

        // IO mode button tooltip
        boolean supportsIOMode = supportsIOModeSupplier != null && supportsIOModeSupplier.get();
        if (supportsIOMode && ioModeButton.isHovered(mouseX, mouseY)) {
            return ioModeButton.getTooltip(mouseX, mouseY);
        }

        // Cards tooltip (from base)
        return super.getTooltip(mouseX, mouseY);
    }
}
