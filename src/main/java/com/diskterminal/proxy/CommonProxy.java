package com.diskterminal.proxy;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import appeng.api.AEApi;

import com.diskterminal.DiskTerminal;
import com.diskterminal.ItemRegistry;
import com.diskterminal.gui.GuiHandler;


public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ItemRegistry.init();
        MinecraftForge.EVENT_BUS.register(new ItemRegistry());
    }

    public void init(FMLInitializationEvent event) {
        DiskTerminal.instance.guiHandler = new GuiHandler();
        NetworkRegistry.INSTANCE.registerGuiHandler(DiskTerminal.instance, DiskTerminal.instance.guiHandler);

        // Register the wireless disk terminal with AE2's wireless registry
        AEApi.instance().registries().wireless().registerWirelessHandler(ItemRegistry.PORTABLE_DISK_TERMINAL);
    }

    public void postInit(FMLPostInitializationEvent event) {
    }
}
