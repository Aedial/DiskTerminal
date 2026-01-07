package com.diskterminal.proxy;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;

import com.diskterminal.ItemRegistry;


public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ItemRegistry.registerModels();
    }
}
