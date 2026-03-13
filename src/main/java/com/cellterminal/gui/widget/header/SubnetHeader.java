package com.cellterminal.gui.widget.header;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;

import com.cellterminal.gui.GuiConstants;


/**
 * Header widget for a subnet entry in the subnet overview tab.
 * <p>
 * Displays:
 * <ul>
 *   <li>Favorite star</li>
 *   <li>Icon (connection icon for subnets, custom icon for main network)</li>
 *   <li>Name (custom name in green, main network in cyan, inaccessible in gray)</li>
 *   <li>Location text (coordinates, below name. Skipped for main network)</li>
 *   <li>Load button (blue button at right edge)</li>
 * </ul>
 *
 * Rename is handled via {@link com.cellterminal.gui.rename.InlineRenameManager}
 * through the base class's {@link #setRenameInfo} mechanism.
 * Double-click highlight uses {@link com.cellterminal.gui.widget.DoubleClickTracker}.
 *
 * @see AbstractHeader
 */
public class SubnetHeader extends AbstractHeader {

    private static final int STAR_X = 2;
    private static final float STAR_SCALING = 0.5f;
    private static final int STAR_SIZE = GuiConstants.FAVORITE_STAR_SIZE;
    private static final int STAR_SCALED_SIZE = (int) (STAR_SIZE * STAR_SCALING);
    private static final int LOAD_BUTTON_MARGIN = 4;

    // Colors
    private static final int COLOR_MAIN_NETWORK = 0xFF00838F;
    private static final int COLOR_NAME_INACCESSIBLE = 0xFF909090;

    // Direction arrow config (null = no arrow, true = outbound →, false = inbound ←)
    private Supplier<Boolean> directionSupplier;
    private static final int ARROW_X = GuiConstants.GUI_INDENT + 18 - 2;
    private static final int ARROW_WIDTH = GuiConstants.SUBNET_ARROWS_SIZE;
    private static final int ARROW_NAME_MARGIN = 2;

    private boolean isMain = false;

    // Subnet state suppliers
    private Supplier<Boolean> isFavoriteSupplier;
    private Supplier<Boolean> canLoadSupplier;
    private Supplier<String> locationSupplier;

    // Callbacks
    private Runnable onStarClick;
    private Runnable onLoadClick;

    // Hover state
    private boolean starHovered = false;
    private boolean loadButtonHovered = false;
    private boolean arrowHovered = false;

    // Cached Load button layout (computed during draw)
    private int loadButtonX;
    private int loadButtonWidth;

    public SubnetHeader(int y, FontRenderer fontRenderer, RenderItem itemRender) {
        super(y, fontRenderer, itemRender);
    }

    public SubnetHeader(int y, FontRenderer fontRenderer, RenderItem itemRender, boolean isMainNetwork) {
        super(y, fontRenderer, itemRender);

        isMain = isMainNetwork;
    }

    // ---- Configuration ----

    /**
     * Set the direction supplier for the connection arrow.
     * When non-null, a colored direction arrow is drawn between the icon and the name.
     * True = outbound (→ green), false = inbound (← blue).
     */
    public void setDirectionSupplier(Supplier<Boolean> supplier) {
        this.directionSupplier = supplier;
    }

    public void setIsFavoriteSupplier(Supplier<Boolean> supplier) {
        this.isFavoriteSupplier = supplier;
    }

    public void setCanLoadSupplier(Supplier<Boolean> supplier) {
        this.canLoadSupplier = supplier;
    }

    public void setLocationSupplier(Supplier<String> supplier) {
        this.locationSupplier = supplier;
    }

    public void setOnStarClick(Runnable callback) {
        this.onStarClick = callback;
    }

    public void setOnLoadClick(Runnable callback) {
        this.onLoadClick = callback;
    }

    // ---- Rendering ----

    @Override
    protected int drawHeaderContent(int mouseX, int mouseY) {
        starHovered = false;
        loadButtonHovered = false;
        arrowHovered = false;

        boolean canLoad = canLoadSupplier != null && canLoadSupplier.get();

        // Calculate Load button position from right edge
        String loadText = I18n.format("cellterminal.subnet.load");
        int loadTextWidth = fontRenderer.getStringWidth(loadText);
        loadButtonWidth = loadTextWidth + LOAD_BUTTON_MARGIN;
        loadButtonX = GuiConstants.CONTENT_RIGHT_EDGE - LOAD_BUTTON_MARGIN - loadTextWidth;

        // Draw direction arrow between icon and name (when connection-level header)
        boolean hasArrow = directionSupplier != null;
        int nameStartX = hasArrow ? (ARROW_X + ARROW_WIDTH + ARROW_NAME_MARGIN) : GuiConstants.HEADER_NAME_X;
        if (hasArrow) {
            drawDirectionArrow();

            int arrowSize = GuiConstants.SUBNET_ARROWS_SIZE;
            int arrowY = y + (height - arrowSize) / 2;
            arrowHovered = mouseX >= ARROW_X && mouseX < ARROW_X + arrowSize
                && mouseY >= arrowY && mouseY < arrowY + arrowSize;
        }

        // Adjust name max width to stop before Load button
        this.nameMaxWidth = loadButtonX - nameStartX - 4;

        // Draw favorite star on left sidebar (2x scale for visibility)
        drawStar(mouseX, mouseY);

        // Draw location text below name (skip for main network)
        if (!isMain) drawLocation();

        // Draw Load button
        boolean isLoadHover = mouseX >= loadButtonX && mouseX < loadButtonX + loadButtonWidth
            && mouseY >= y && mouseY < y + 10;
        loadButtonHovered = isLoadHover;
        drawLoadButton(loadButtonX, y, loadText, isLoadHover, canLoad);

        // Return hover right bound (up to Load button area)
        return loadButtonX;
    }

    /**
     * Draw the favorite star using atlas texture.
     * Atlas layout: ux = 0 for on, ux = SIZE for off; uy = 0 for normal, uy = SIZE for hovered.
     */
    private void drawStar(int mouseX, int mouseY) {
        boolean isFav = isFavoriteSupplier != null && isFavoriteSupplier.get();

        int offset = (GuiConstants.ROW_HEIGHT - STAR_SCALED_SIZE) / 2;

        // Check star hover
        starHovered = mouseX >= STAR_X + offset && mouseX < STAR_X + STAR_SCALED_SIZE + offset
            && mouseY >= y && mouseY < y + GuiConstants.ROW_HEIGHT;

        int size = STAR_SIZE;
        int texX = GuiConstants.FAVORITE_STAR_X + (isFav ? 0 : size);
        int texY = GuiConstants.FAVORITE_STAR_Y + (starHovered ? size : 0);

        GuiConstants.drawAtlasSpriteScaled(STAR_X + offset, y + offset, texX, texY, size, size, STAR_SCALING);
    }

    /**
     * Draw the direction arrow using atlas texture.
     * Atlas layout: uy = 0 for → outbound, uy = SIZE for ← inbound.
     */
    private void drawDirectionArrow() {
        boolean outbound = directionSupplier.get();

        int size = GuiConstants.SUBNET_ARROWS_SIZE;
        int texX = GuiConstants.SUBNET_ARROWS_X;
        int texY = GuiConstants.SUBNET_ARROWS_Y + (outbound ? 0 : size);

        GuiConstants.drawAtlasSprite(ARROW_X, y + (height - size) / 2, texX, texY, size);
    }

    /**
     * Draw the location string below the name.
     */
    private void drawLocation() {
        String location = locationSupplier != null ? locationSupplier.get() : "";
        if (location.isEmpty()) return;

        // Location starts at the same X as the name (adjusted for arrow presence)
        int locationX = directionSupplier != null ? (ARROW_X + ARROW_WIDTH + ARROW_NAME_MARGIN) : GuiConstants.HEADER_NAME_X;
        int locationMaxWidth = GuiConstants.CONTENT_RIGHT_EDGE - locationX - 4;
        String displayLocation = trimTextToWidth(location, locationMaxWidth);
        fontRenderer.drawString(displayLocation, locationX, y + 9, GuiConstants.COLOR_TEXT_SECONDARY);
    }

    /**
     * Draw the Load button at the right edge of the header.
     */
    private void drawLoadButton(int x, int btnY, String text, boolean isHovered, boolean isEnabled) {
        int buttonHeight = 10;

        // Background
        int bgColor;
        if (!isEnabled) {
            bgColor = 0xFF808080;
        } else if (isHovered) {
            bgColor = 0xFF4A90D9;
        } else {
            bgColor = 0xFF3B7DC9;
        }
        Gui.drawRect(x, btnY, x + loadButtonWidth, btnY + buttonHeight, bgColor);

        // Border (3D effect)
        int highlightColor = isEnabled ? 0xFF6BA5E7 : 0xFFA0A0A0;
        int shadowColor = isEnabled ? 0xFF2A5B8A : 0xFF606060;
        Gui.drawRect(x, btnY, x + loadButtonWidth, btnY + 1, highlightColor);
        Gui.drawRect(x, btnY, x + 1, btnY + buttonHeight, highlightColor);
        Gui.drawRect(x, btnY + buttonHeight - 1, x + loadButtonWidth, btnY + buttonHeight, shadowColor);
        Gui.drawRect(x + loadButtonWidth - 1, btnY, x + loadButtonWidth, btnY + buttonHeight, shadowColor);

        // Text
        int textX = x + LOAD_BUTTON_MARGIN / 2;
        int textY = btnY + 1;
        int textColor = isEnabled ? 0xFFFFFFFF : 0xFFC0C0C0;
        fontRenderer.drawString(text, textX, textY, textColor);
    }

    /**
     * Override icon drawing: main network gets an atlas icon, subnets get normal item icon.
     */
    @Override
    protected void drawIcon() {
        if (isMain) {
            int size = GuiConstants.MAIN_NET_ICON_SIZE;
            GuiConstants.drawAtlasSprite(
                GuiConstants.GUI_INDENT + (ICON_SIZE - GuiConstants.MAIN_NET_ICON_SIZE) / 2,
                y + (ICON_SIZE - GuiConstants.MAIN_NET_ICON_SIZE) / 2,
                GuiConstants.MAIN_NET_ICON_X, GuiConstants.MAIN_NET_ICON_Y, size);
        } else {
            super.drawIcon();
        }
    }

    /**
     * Override name drawing for subnet-specific coloring:
     * - Main network: cyan
     * - Inaccessible: gray
     * - Custom name: green
     * - Default: normal text color
     * <p>
     * Also vertically center the name for main network (no location line).
     */
    @Override
    protected void drawName(int mouseX, int mouseY) {
        String name = nameSupplier != null ? nameSupplier.get() : "";
        if (name.isEmpty()) return;

        boolean canLoad = canLoadSupplier != null && canLoadSupplier.get();

        String displayName = trimTextToWidth(name, nameMaxWidth);

        int nameColor;
        if (isMain) {
            nameColor = COLOR_MAIN_NETWORK;
        } else if (!canLoad) {
            nameColor = COLOR_NAME_INACCESSIBLE;
        } else if (hasCustomNameSupplier != null && hasCustomNameSupplier.get()) {
            nameColor = GuiConstants.COLOR_CUSTOM_NAME;
        } else {
            nameColor = GuiConstants.COLOR_TEXT_NORMAL;
        }

        // Main network has no location line, so center the name vertically
        int nameX = directionSupplier != null ? (ARROW_X + ARROW_WIDTH + ARROW_NAME_MARGIN) : GuiConstants.HEADER_NAME_X;
        int nameY = isMain ? (y + 5) : (y + 1);
        fontRenderer.drawString(displayName, nameX, nameY, nameColor);

        // Check name hover for rename interaction
        if (mouseX >= nameX && mouseX < nameX + nameMaxWidth
            && mouseY >= nameY && mouseY < nameY + 9) {
            nameHovered = true;
        }
    }

    // ---- Click handling ----

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) return false;

        // Star click (left-click only)
        if (button == 0 && starHovered && onStarClick != null) {
            onStarClick.run();
            return true;
        }

        // Load button click (left-click only)
        if (button == 0 && loadButtonHovered && onLoadClick != null) {
            boolean canLoad = canLoadSupplier != null && canLoadSupplier.get();
            if (canLoad) {
                onLoadClick.run();
                return true;
            }
        }

        // Delegate to base for rename (right-click on name) and double-click (header area)
        return super.handleClick(mouseX, mouseY, button);
    }

    // ---- Tooltips ----

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return Collections.emptyList();

        // Star tooltip
        if (starHovered) {
            return Collections.singletonList(I18n.format("cellterminal.subnet.controls.star"));
        }

        // Main network tooltip
        if (isMain) {
            List<String> lines = new ArrayList<>();
            lines.add(I18n.format("cellterminal.subnet.main_network"));
            lines.add("§e" + I18n.format("cellterminal.subnet.click_load_main"));
            return lines;
        }

        // Load button tooltip
        if (loadButtonHovered) return getLoadButtonTooltip();

        // Direction arrow tooltip
        if (arrowHovered && directionSupplier != null) {
            boolean outbound = directionSupplier.get();
            String directionKey = "cellterminal.subnet.direction." + (outbound ? "outbound" : "inbound");

            List<String> lines = new ArrayList<>();
            lines.add(I18n.format(directionKey));
            lines.add("");
            lines.add("§7" + I18n.format(directionKey + ".desc"));

            return lines;
        }

        // Default tooltip (if any)
        return super.getTooltip(mouseX, mouseY);
    }

    private List<String> getLoadButtonTooltip() {
        boolean canLoad = canLoadSupplier != null && canLoadSupplier.get();
        List<String> lines = new ArrayList<>();

        if (!canLoad) {
            // Distinguish between no power and no access
            // We don't have direct access to hasPower/isAccessible here,
            // so use the generic disabled tooltip
            lines.add("§c" + I18n.format("cellterminal.subnet.load.disabled"));
        } else {
            lines.add(I18n.format("cellterminal.subnet.load.tooltip"));
        }

        return lines;
    }
}
