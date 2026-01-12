package com.cellterminal.proxy;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

import com.cellterminal.ItemRegistry;
import com.cellterminal.client.BlockHighlightRenderer;


public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ItemRegistry.registerModels();

        // Register block highlight renderer
        MinecraftForge.EVENT_BUS.register(new BlockHighlightRenderer());
    }
}
