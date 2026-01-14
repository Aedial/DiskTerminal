package com.cellterminal.proxy;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.cellterminal.ItemRegistry;
import com.cellterminal.client.BlockHighlightRenderer;
import com.cellterminal.client.KeyBindings;


public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        // Register keybindings
        KeyBindings.registerAll();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ItemRegistry.registerModels();

        // Register block highlight renderer
        MinecraftForge.EVENT_BUS.register(new BlockHighlightRenderer());
    }
}
