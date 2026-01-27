package com.cellterminal.proxy;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import appeng.api.AEApi;

import com.cellterminal.CellTerminal;
import com.cellterminal.ItemRegistry;
import com.cellterminal.config.CellTerminalServerConfig;
import com.cellterminal.gui.GuiHandler;
import com.cellterminal.integration.AE2WUTIntegration;


public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // Initialize server config (applies to both client and server)
        CellTerminalServerConfig.init(event.getModConfigurationDirectory());

        ItemRegistry.init();
        MinecraftForge.EVENT_BUS.register(new ItemRegistry());
    }

    public void init(FMLInitializationEvent event) {
        CellTerminal.instance.guiHandler = new GuiHandler();
        NetworkRegistry.INSTANCE.registerGuiHandler(CellTerminal.instance, CellTerminal.instance.guiHandler);

        // Register the wireless cell terminal with AE2's wireless registry
        AEApi.instance().registries().wireless().registerWirelessHandler(ItemRegistry.WIRELESS_CELL_TERMINAL);

        // Register with AE2WUT if present
        AE2WUTIntegration.registerContainer();

        // Register Cell Terminal recipe ingredient with AE2WUT if present
        AE2WUTIntegration.registerRecipeIngredient();
    }

    public void postInit(FMLPostInitializationEvent event) {
    }
}
