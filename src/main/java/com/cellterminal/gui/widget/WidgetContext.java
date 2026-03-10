package com.cellterminal.gui.widget;

import java.util.Map;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;

import com.cellterminal.client.StorageInfo;
import com.cellterminal.gui.render.RenderContext;


/**
 * Context object passed to widgets during rendering and event handling.
 *
 * Provides access to shared resources (font renderer, item renderer) and
 * GUI positioning data without requiring widgets to hold direct GUI references.
 *
 * This replaces the scattered context information that was previously passed
 * as individual parameters throughout the render pipeline.
 */
public class WidgetContext {

    /** Font renderer for text drawing */
    public final FontRenderer fontRenderer;

    /** Item renderer for ItemStack drawing */
    public final RenderItem itemRender;

    /** GUI left offset (absolute screen coordinates) */
    public final int guiLeft;

    /** GUI top offset (absolute screen coordinates) */
    public final int guiTop;

    /** Number of visible rows in the scrollable area */
    public final int rowsVisible;

    /** Y position where content starts (below header) */
    public final int contentStartY;

    /** Map of storage IDs to StorageInfo, for looking up parent storages */
    public final Map<Long, StorageInfo> storageMap;

    /**
     * Legacy render context for compatibility during migration.
     * Widgets that still need to interact with the old render context can access
     * it here. This will be removed once all widgets are fully migrated.
     */
    public final RenderContext legacyContext;

    public WidgetContext(FontRenderer fontRenderer, RenderItem itemRender,
                         int guiLeft, int guiTop, int rowsVisible, int contentStartY,
                         Map<Long, StorageInfo> storageMap, RenderContext legacyContext) {
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        this.rowsVisible = rowsVisible;
        this.contentStartY = contentStartY;
        this.storageMap = storageMap;
        this.legacyContext = legacyContext;
    }

    /**
     * Convert a GUI-relative Y position to absolute screen coordinates.
     */
    public int toAbsoluteY(int relativeY) {
        return guiTop + relativeY;
    }

    /**
     * Convert a GUI-relative X position to absolute screen coordinates.
     */
    public int toAbsoluteX(int relativeX) {
        return guiLeft + relativeX;
    }
}
