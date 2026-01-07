package com.diskterminal;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;

import com.diskterminal.items.ItemDiskTerminal;
import com.diskterminal.items.ItemPortableDiskTerminal;
import com.diskterminal.part.PartDiskTerminal;


public class ItemRegistry {

    public static ItemDiskTerminal DISK_TERMINAL;
    public static ItemPortableDiskTerminal PORTABLE_DISK_TERMINAL;

    public static void init() {
        DISK_TERMINAL = new ItemDiskTerminal();
        PORTABLE_DISK_TERMINAL = new ItemPortableDiskTerminal();
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(DISK_TERMINAL);
        event.getRegistry().register(PORTABLE_DISK_TERMINAL);

        AEApi.instance().registries().partModels().registerModels(PartDiskTerminal.getResources());
    }

    @SideOnly(Side.CLIENT)
    public static void registerModels() {
        registerModel(DISK_TERMINAL);
        registerModel(PORTABLE_DISK_TERMINAL);
    }

    @SideOnly(Side.CLIENT)
    private static void registerModel(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
            new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
