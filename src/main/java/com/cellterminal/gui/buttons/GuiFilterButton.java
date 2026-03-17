package com.cellterminal.gui.buttons;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

import appeng.api.AEApi;

import com.cellterminal.CellTerminal;
import com.cellterminal.client.CellFilter;
import com.cellterminal.client.CellFilter.State;
import com.cellterminal.gui.GuiConstants;


/**
 * A toggle button for cell/storage bus filters.
 * Cycles through three states: SHOW_ALL (neutral), SHOW_ONLY (green), HIDE (red).
 */
public class GuiFilterButton extends GuiAtlasButton {

    private final CellFilter filter;
    private State currentState;

    // Colors for different states
    public static final int SIZE = GuiConstants.TERMINAL_SIDE_BUTTON_SIZE;

    private static final ItemStack ITEM_CELL_ICON = new ItemStack(Blocks.STONE);
    private static final ItemStack FLUID_CELL_ICON = new ItemStack(Items.BUCKET);
    private static final ItemStack ESSENTIA_CELL_ICON = getEssentiaIcon();
    private static final ItemStack GAS_CELL_ICON = getGasIcon();
    private static final ItemStack HAS_ITEMS_ICON = new ItemStack(Blocks.CHEST);
    private static final ItemStack PARTITIONED_ICON = AEApi.instance().definitions().blocks().
        cellWorkbench().maybeStack(1).orElse(ItemStack.EMPTY);

    public GuiFilterButton(int buttonId, int x, int y, CellFilter filter, State initialState) {
        super(buttonId, x, y, SIZE);
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
    protected int getBackgroundTexX() {
        switch (currentState) {
            case SHOW_ONLY:
                return GuiConstants.TERMINAL_STYLE_BUTTON_X + SIZE;  // Green background
            case HIDE:
                return GuiConstants.TERMINAL_STYLE_BUTTON_X + 2 * SIZE;  // Red background
            default:
                return GuiConstants.TERMINAL_STYLE_BUTTON_X;  // Default background
        }
    }

    @Override
    protected int getBackgroundTexY() {
        return GuiConstants.TERMINAL_STYLE_BUTTON_Y + (this.hovered ? SIZE : 0);
    }

    @Override
    protected void drawForeground(Minecraft mc) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
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
            case GAS_CELLS:
                iconStack = GAS_CELL_ICON;
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
            Class<?> thaumcraftItems = Class.forName("thaumcraft.api.items.ItemsTC");
            Object jarItem = thaumcraftItems.getField("phial").get(null);
            if (jarItem instanceof Item) return new ItemStack((Item) jarItem);
        } catch (Exception e) {
            CellTerminal.LOGGER.warn("Failed to get Thaumcraft jar item for essentia cell filter icon", e);
        }

        return new ItemStack(Items.GLASS_BOTTLE);
    }

    private static ItemStack getGasIcon() {
        // Try to get Mekanism Gas Tank, fall back to bucket if not available
        if (!Loader.isModLoaded("mekanism")) return new ItemStack(Items.BUCKET);

        try {
            Class<?> mekanismBlocks = Class.forName("mekanism.common.MekanismBlocks");
            Object gasTankBlock = mekanismBlocks.getField("GasTank").get(null);
            if (gasTankBlock instanceof Block) return new ItemStack((Block) gasTankBlock);
        } catch (Exception e) {
            CellTerminal.LOGGER.warn("Failed to get Mekanism Gas Tank block for gas cell filter icon", e);
        }

        return new ItemStack(Items.BUCKET);
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
}
