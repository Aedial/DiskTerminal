package com.cellterminal;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;

import com.cellterminal.items.ItemCellTerminal;
import com.cellterminal.items.ItemWirelessCellTerminal;
import com.cellterminal.part.PartCellTerminal;


public class ItemRegistry {

    public static ItemCellTerminal CELL_TERMINAL;
    public static ItemWirelessCellTerminal WIRELESS_CELL_TERMINAL;

    public static void init() {
        CELL_TERMINAL = new ItemCellTerminal();
        WIRELESS_CELL_TERMINAL = new ItemWirelessCellTerminal();
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(CELL_TERMINAL);
        event.getRegistry().register(WIRELESS_CELL_TERMINAL);

        AEApi.instance().registries().partModels().registerModels(PartCellTerminal.getResources());
    }

    @SideOnly(Side.CLIENT)
    public static void registerModels() {
        registerModel(CELL_TERMINAL);
        registerModel(WIRELESS_CELL_TERMINAL);
    }

    @SideOnly(Side.CLIENT)
    private static void registerModel(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
            new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
