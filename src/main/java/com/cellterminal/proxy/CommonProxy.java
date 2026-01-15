package com.cellterminal.proxy;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import appeng.api.AEApi;

import com.cellterminal.CellTerminal;
import com.cellterminal.ItemRegistry;
import com.cellterminal.events.PriorityWandEventHandler;
import com.cellterminal.gui.GuiHandler;
import com.cellterminal.items.cells.compacting.CompactingCellHandler;
import com.cellterminal.items.cells.highdensity.HighDensityCellHandler;
import com.cellterminal.items.cells.highdensity.HighDensityCompactingCellHandler;


public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ItemRegistry.init();
        MinecraftForge.EVENT_BUS.register(new ItemRegistry());
        MinecraftForge.EVENT_BUS.register(new PriorityWandEventHandler());
    }

    public void init(FMLInitializationEvent event) {
        CellTerminal.instance.guiHandler = new GuiHandler();
        NetworkRegistry.INSTANCE.registerGuiHandler(CellTerminal.instance, CellTerminal.instance.guiHandler);

        // Register the wireless cell terminal with AE2's wireless registry
        AEApi.instance().registries().wireless().registerWirelessHandler(ItemRegistry.WIRELESS_CELL_TERMINAL);

        // Register the compacting cell handler with AE2 (must be done in init, after AE2's BasicCellHandler)
        AEApi.instance().registries().cell().addCellHandler(new CompactingCellHandler());

        // Register the high-density cell handler with AE2
        AEApi.instance().registries().cell().addCellHandler(new HighDensityCellHandler());

        // Register the high-density compacting cell handler with AE2
        AEApi.instance().registries().cell().addCellHandler(new HighDensityCompactingCellHandler());
    }

    public void postInit(FMLPostInitializationEvent event) {
    }
}
