package com.cellterminal.gui.widget.line;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import com.cellterminal.gui.GuiConstants;
import com.cellterminal.gui.widget.AbstractWidget;
import com.cellterminal.gui.widget.button.SmallButton;


/**
 * Base class for all line widgets in the Cell Terminal GUI.
 *
 * A line is a single row in the scrollable content area. It handles:
 * - Tree line rendering (vertical + horizontal connectors)
 * - Optional small button at the tree line junction
 * - Base layout and hover detection
 *
 * Subclasses add slot grids, cell slots, cards, and tab-specific elements.
 *
 * <h3>Tree line model</h3>
 * Each row draws its own tree connector going <b>upward</b> to the previous row:
 * <pre>
 *   |   &lt;- vertical line UP to the row above, cut at its exposed cut point
 *   +-- &lt;- horizontal branch at this row
 *       &lt;- nothing below; the next row handles the vertical line up to us
 * </pre>
 * The parent tab widget manages the cut point propagation between rows:
 * <ol>
 *   <li>Set {@code lineAboveCutY} on each row (from the previous row's {@link #getTreeLineCutY()})</li>
 *   <li>Each row draws its vertical + branch based on that single value</li>
 *   <li>Edge cases (first row after header, scrolled content) are handled by the tab</li>
 * </ol>
 */
public abstract class AbstractLine extends AbstractWidget {

    /** X position of the vertical tree line */
    protected static final int TREE_LINE_X = GuiConstants.GUI_INDENT + 7;

    /** Width of the horizontal tree branch */
    protected static final int TREE_BRANCH_WIDTH = 10;

    // Tree line state (set by parent tab before drawing)
    protected boolean drawTreeLine = true;

    /**
     * Y coordinate where the vertical line going UP should start.
     * Set by the parent tab from the previous row's {@link #getTreeLineCutY()},
     * or from the visible top / header connector Y for edge cases.
     */
    protected int lineAboveCutY = GuiConstants.CONTENT_START_Y;

    /** Whether this is the first row for a cell (controls cell slot, cards, etc.) */
    protected boolean isFirstRow = true;

    /** Optional button at the tree line junction (e.g. DoPartition, ClearPartition) */
    protected SmallButton treeButton;

    protected AbstractLine(int x, int y, int width) {
        super(x, y, width, GuiConstants.ROW_HEIGHT);
    }

    /**
     * Configure the tree line parameters for this line.
     * Called by the parent tab widget before each draw cycle,
     * since connectivity depends on neighboring lines.
     *
     * @param drawTreeLine Whether to draw tree lines at all
     * @param lineAboveCutY Y limit from the row above (or header/visible top)
     */
    public void setTreeLineParams(boolean drawTreeLine, int lineAboveCutY) {
        this.drawTreeLine = drawTreeLine;
        this.lineAboveCutY = lineAboveCutY;
    }

    /**
     * Get the Y coordinate below which the next row can draw its vertical line
     * up to us. This accounts for any button or icon at the tree junction.
     *
     * @return Y coordinate of the bottom edge of the junction area
     */
    public int getTreeLineCutY() {
        if (treeButton != null) {
            // Button background extends from y+4 to y+4+SIZE+4 (buttonY-1 to buttonY+SIZE+1)
            // Extend 1px closer to the button (reduced gap)
            return y + 5 + GuiConstants.SMALL_BUTTON_SIZE + 1;
        }

        // Default: bottom of the horizontal branch
        return y + 9;
    }

    /**
     * Set the optional button at the tree line junction.
     */
    public void setTreeButton(SmallButton button) {
        this.treeButton = button;
    }

    /**
     * Get the tree junction button, if any.
     */
    public SmallButton getTreeButton() {
        return treeButton;
    }

    /**
     * Draw the tree line connector for this row.
     * Draws a vertical line UP from {@code lineAboveCutY} to the junction,
     * a horizontal branch, and an optional button covering the junction.
     */
    protected void drawTreeLines(int mouseX, int mouseY) {
        if (!drawTreeLine) return;

        int branchY = y + 8;

        // Where the vertical line ends depends on whether there's a button
        // covering the junction. If there is, stop at the top of the button
        // background so the button is drawn on top cleanly.
        // Extend 1px closer to the button (reduced gap)
        int verticalEndY = (treeButton != null) ? y + 4 : branchY;

        // Vertical line from the row above's cut point down to our junction
        if (lineAboveCutY < verticalEndY) {
            Gui.drawRect(TREE_LINE_X, lineAboveCutY, TREE_LINE_X + 1, verticalEndY, GuiConstants.COLOR_TREE_LINE);
        }

        // Horizontal branch
        Gui.drawRect(TREE_LINE_X, branchY, TREE_LINE_X + TREE_BRANCH_WIDTH, branchY + 1, GuiConstants.COLOR_TREE_LINE);

        // Draw tree junction button if present (covers part of tree line)
        if (treeButton != null) {
            int buttonX = TREE_LINE_X - 5;
            int buttonY = y + 5;
            treeButton.setPosition(buttonX, buttonY);

            treeButton.draw(mouseX, mouseY);
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) return false;

        // Check tree button first
        if (treeButton != null && treeButton.isVisible()
            && treeButton.isHovered(mouseX, mouseY)) {
            return treeButton.handleClick(mouseX, mouseY, button);
        }

        return false;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return Collections.emptyList();

        // Check tree button tooltip
        if (treeButton != null && treeButton.isHovered(mouseX, mouseY)) {
            return treeButton.getTooltip(mouseX, mouseY);
        }

        return Collections.emptyList();
    }
}
