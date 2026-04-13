package com.cellterminal.integration;

import net.minecraft.item.ItemStack;

import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IAdvancedGuiHandler;

import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.GuiCellTerminalBase;
import com.cellterminal.gui.handler.QuickPartitionHandler;


/**
 * JEI Plugin for Cell Terminal.
 * Provides JEI integration including ghost ingredient handler and runtime access.
 */
@JEIPlugin
public class CellTerminalJEIPlugin implements IModPlugin {

    @Override
    public void register(IModRegistry registry) {
        // Respect server config: allow disabling JEI integration
        if (CellTerminalServerConfig.isInitialized()
            && !CellTerminalServerConfig.getInstance().isIntegrationJeiEnabled()) {
            return;
        }

        // Register advanced GUI handlers for exclusion areas if needed
        registry.addAdvancedGuiHandlers(new CellTerminalGuiHandler());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (CellTerminalServerConfig.isInitialized()
            && !CellTerminalServerConfig.getInstance().isIntegrationJeiEnabled()) {
            return;
        }

        // Provide the JEI runtime to the quick partition handler
        QuickPartitionHandler.setJeiRuntime(runtime);
    }

    /**
     * Advanced GUI handler for Cell Terminal GUIs.
     * Provides ingredient detection for virtual slots so JEI keybinds
     * (Show Recipes, Show Uses, Bookmark) work on items displayed in
     * our custom slot widgets (not real MC inventory slots).
     */
    private static class CellTerminalGuiHandler implements IAdvancedGuiHandler<GuiCellTerminalBase> {

        @Override
        public Class<GuiCellTerminalBase> getGuiContainerClass() {
            return GuiCellTerminalBase.class;
        }

        @Override
        public ItemStack getIngredientUnderMouse(GuiCellTerminalBase gui, int mouseX, int mouseY) {
            ItemStack stack = gui.getVirtualHoveredItemStack(mouseX, mouseY);
            if (stack != null && !stack.isEmpty()) return stack;

            return null;
        }
    }
}
