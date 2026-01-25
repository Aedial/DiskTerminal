package com.cellterminal.proxy;

import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.cellterminal.ItemRegistry;
import com.cellterminal.client.BlockHighlightRenderer;
import com.cellterminal.client.KeyBindings;
import com.cellterminal.client.UpgradeTooltipHandler;


public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        // Register keybindings
        KeyBindings.registerAll();

        // Register this proxy for model registration events
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onModelRegistry(ModelRegistryEvent event) {
        ItemRegistry.registerModels();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Register block highlight renderer
        MinecraftForge.EVENT_BUS.register(new BlockHighlightRenderer());

        // Register upgrade tooltip handler
        MinecraftForge.EVENT_BUS.register(new UpgradeTooltipHandler());
    }
}
