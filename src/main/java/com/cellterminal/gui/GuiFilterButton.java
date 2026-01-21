package com.cellterminal.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

import appeng.api.AEApi;

import com.cellterminal.CellTerminal;
import com.cellterminal.client.CellFilter;
import com.cellterminal.client.CellFilter.State;


/**
 * A toggle button for cell/storage bus filters.
 * Cycles through three states: SHOW_ALL (neutral), SHOW_ONLY (green), HIDE (red).
 */
public class GuiFilterButton extends GuiButton {

    public static final int BUTTON_SIZE = 16;

    private final CellFilter filter;
    private State currentState;

    // Colors for different states
    private static final int COLOR_NEUTRAL = 0xFF8B8B8B;  // Grey - show all
    private static final int COLOR_SHOW = 0xFF4CAF50;     // Green - show only
    private static final int COLOR_HIDE = 0xFFE53935;     // Red - hide

    private static final ItemStack ITEM_CELL_ICON = new ItemStack(Blocks.STONE);
    private static final ItemStack FLUID_CELL_ICON = new ItemStack(Items.BUCKET);
    private static final ItemStack ESSENTIA_CELL_ICON = getEssentiaIcon();
    private static final ItemStack HAS_ITEMS_ICON = new ItemStack(Blocks.CHEST);
    private static final ItemStack PARTITIONED_ICON = AEApi.instance().definitions().blocks().
        cellWorkbench().maybeStack(1).orElse(ItemStack.EMPTY);

    public GuiFilterButton(int buttonId, int x, int y, CellFilter filter, State initialState) {
        super(buttonId, x, y, BUTTON_SIZE, BUTTON_SIZE, "");
        this.filter = filter;
        this.currentState = initialState;
    }

    public CellFilter getFilter() {
        return filter;
    }

    public State getState() {
        return currentState;
    }

    public void setState(State state) {
        this.currentState = state;
    }

    public State cycleState() {
        this.currentState = this.currentState.next();

        return this.currentState;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        // Draw button background based on state
        int bgColor = getBackgroundColor();
        if (this.hovered) bgColor = brightenColor(bgColor, 0.2f);

        drawRect(this.x, this.y, this.x + this.width, this.y + this.height, bgColor);

        // Draw 3D border
        drawRect(this.x, this.y, this.x + this.width, this.y + 1, brightenColor(bgColor, 0.3f));
        drawRect(this.x, this.y, this.x + 1, this.y + this.height, brightenColor(bgColor, 0.3f));
        drawRect(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, darkenColor(bgColor, 0.3f));
        drawRect(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, darkenColor(bgColor, 0.3f));

        // Draw filter icon
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        drawFilterIcon(mc);
    }

    private int getBackgroundColor() {
        switch (currentState) {
            case SHOW_ONLY:
                return COLOR_SHOW;
            case HIDE:
                return COLOR_HIDE;
            default:
                return COLOR_NEUTRAL;
        }
    }

    private void drawFilterIcon(Minecraft mc) {
        ItemStack iconStack;

        switch (filter) {
            case ITEM_CELLS:
                iconStack = ITEM_CELL_ICON;
                break;
            case FLUID_CELLS:
                iconStack = FLUID_CELL_ICON;
                break;
            case ESSENTIA_CELLS:
                iconStack = ESSENTIA_CELL_ICON;
                break;
            case HAS_ITEMS:
                iconStack = HAS_ITEMS_ICON;
                break;
            case PARTITIONED:
                iconStack = PARTITIONED_ICON;
                break;
            default:
                iconStack = ItemStack.EMPTY;
        }

        if (!iconStack.isEmpty()) renderItemIcon(mc, iconStack);
    }

    private static ItemStack getEssentiaIcon() {
        // Try to get Thaumcraft Jar, fall back to glass bottle if not available
        if (!Loader.isModLoaded("thaumcraft")) return new ItemStack(Items.GLASS_BOTTLE);

        try {
            // Class<?> thaumcraftBlocks = Class.forName("thaumcraft.api.blocks.BlocksTC");
            // Object jarBlock = thaumcraftBlocks.getField("jarNormal").get(null);
            // if (jarBlock instanceof Block) return new ItemStack((Block) jarBlock);
            Class<?> thaumcraftItems = Class.forName("thaumcraft.api.items.ItemsTC");
            Object jarItem = thaumcraftItems.getField("phial").get(null);
            if (jarItem instanceof Item) return new ItemStack((Item) jarItem);
        } catch (Exception e) {
            CellTerminal.LOGGER.warn("Failed to get Thaumcraft jar item for essentia cell filter icon", e);
        }

        return new ItemStack(Items.GLASS_BOTTLE);
    }

    private void renderItemIcon(Minecraft mc, ItemStack stack) {
        RenderItem itemRender = mc.getRenderItem();

        GlStateManager.pushMatrix();
        GlStateManager.translate(this.x, this.y, 0);
        GlStateManager.scale(0.75F, 0.75F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();

        itemRender.renderItemAndEffectIntoGUI(stack, 2, 2);

        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    /**
     * Get the tooltip lines for this filter button.
     */
    public List<String> getTooltip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.cellterminal.filter." + filter.getConfigKey()));

        String stateKey;
        String colorCode;
        switch (currentState) {
            case SHOW_ONLY:
                stateKey = "gui.cellterminal.filter.state.show_only";
                colorCode = "§a";  // Green
                break;
            case HIDE:
                stateKey = "gui.cellterminal.filter.state.hide";
                colorCode = "§c";  // Red
                break;
            default:
                stateKey = "gui.cellterminal.filter.state.show_all";
                colorCode = "§7";  // Grey
        }
        tooltip.add(colorCode + I18n.format(stateKey));

        return tooltip;
    }

    private static int brightenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * (1 + factor)));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * (1 + factor)));
        int b = Math.min(255, (int) ((color & 0xFF) * (1 + factor)));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * (1 - factor));
        int g = (int) (((color >> 8) & 0xFF) * (1 - factor));
        int b = (int) ((color & 0xFF) * (1 - factor));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
