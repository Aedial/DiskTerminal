package com.cellterminal.integration;

import net.minecraft.item.ItemStack;

import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IAdvancedGuiHandler;

import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.GuiCellTerminal;
import com.cellterminal.gui.GuiWirelessCellTerminal;
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
     * Can be extended to provide exclusion areas for JEI.
     */
    private static class CellTerminalGuiHandler implements IAdvancedGuiHandler<GuiCellTerminal> {

        @Override
        public Class<GuiCellTerminal> getGuiContainerClass() {
            return GuiCellTerminal.class;
        }

        @Override
        public ItemStack getIngredientUnderMouse(GuiCellTerminal gui, int mouseX, int mouseY) {
            // Return null to use default behavior
            return null;
        }
    }
}
