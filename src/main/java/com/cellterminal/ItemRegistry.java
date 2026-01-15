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
import com.cellterminal.items.ItemOverflowCard;
import com.cellterminal.items.ItemPriorityWand;
import com.cellterminal.items.ItemWirelessCellTerminal;
import com.cellterminal.items.cells.compacting.ItemCompactingCell;
import com.cellterminal.items.cells.compacting.ItemCompactingComponent;
import com.cellterminal.items.cells.highdensity.ItemHighDensityCell;
import com.cellterminal.items.cells.highdensity.ItemHighDensityCompactingCell;
import com.cellterminal.items.cells.highdensity.ItemHighDensityCompactingComponent;
import com.cellterminal.items.cells.highdensity.ItemHighDensityComponent;
import com.cellterminal.items.cells.normal.ItemNormalStorageCell;
import com.cellterminal.items.cells.normal.ItemNormalStorageComponent;
import com.cellterminal.part.PartCellTerminal;


public class ItemRegistry {

    public static ItemCellTerminal CELL_TERMINAL;
    public static ItemWirelessCellTerminal WIRELESS_CELL_TERMINAL;
    public static ItemCompactingCell COMPACTING_CELL;
    public static ItemCompactingComponent COMPACTING_COMPONENT;
    public static ItemHighDensityCell HIGH_DENSITY_CELL;
    public static ItemHighDensityComponent HIGH_DENSITY_COMPONENT;
    public static ItemHighDensityCompactingCell HIGH_DENSITY_COMPACTING_CELL;
    public static ItemHighDensityCompactingComponent HIGH_DENSITY_COMPACTING_COMPONENT;
    public static ItemNormalStorageCell NORMAL_STORAGE_CELL;
    public static ItemNormalStorageComponent NORMAL_STORAGE_COMPONENT;
    public static ItemOverflowCard OVERFLOW_CARD;
    public static ItemPriorityWand PRIORITY_WAND;

    public static void init() {
        CELL_TERMINAL = new ItemCellTerminal();
        WIRELESS_CELL_TERMINAL = new ItemWirelessCellTerminal();
        COMPACTING_CELL = new ItemCompactingCell();
        COMPACTING_COMPONENT = new ItemCompactingComponent();
        HIGH_DENSITY_CELL = new ItemHighDensityCell();
        HIGH_DENSITY_COMPONENT = new ItemHighDensityComponent();
        HIGH_DENSITY_COMPACTING_CELL = new ItemHighDensityCompactingCell();
        HIGH_DENSITY_COMPACTING_COMPONENT = new ItemHighDensityCompactingComponent();
        NORMAL_STORAGE_CELL = new ItemNormalStorageCell();
        NORMAL_STORAGE_COMPONENT = new ItemNormalStorageComponent();
        OVERFLOW_CARD = new ItemOverflowCard();
        PRIORITY_WAND = new ItemPriorityWand();
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(CELL_TERMINAL);
        event.getRegistry().register(WIRELESS_CELL_TERMINAL);
        event.getRegistry().register(COMPACTING_CELL);
        event.getRegistry().register(COMPACTING_COMPONENT);
        event.getRegistry().register(HIGH_DENSITY_CELL);
        event.getRegistry().register(HIGH_DENSITY_COMPONENT);
        event.getRegistry().register(HIGH_DENSITY_COMPACTING_CELL);
        event.getRegistry().register(HIGH_DENSITY_COMPACTING_COMPONENT);
        event.getRegistry().register(NORMAL_STORAGE_CELL);
        event.getRegistry().register(NORMAL_STORAGE_COMPONENT);
        event.getRegistry().register(OVERFLOW_CARD);
        event.getRegistry().register(PRIORITY_WAND);

        AEApi.instance().registries().partModels().registerModels(PartCellTerminal.getResources());
    }

    @SideOnly(Side.CLIENT)
    public static void registerModels() {
        registerModel(CELL_TERMINAL);
        registerModel(WIRELESS_CELL_TERMINAL);
        registerModel(OVERFLOW_CARD);
        registerModel(PRIORITY_WAND);

        // Register compacting cell models for each tier
        String[] cellTiers = ItemCompactingCell.getTierNames();
        for (int i = 0; i < cellTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(COMPACTING_CELL, i,
                new ModelResourceLocation(COMPACTING_CELL.getRegistryName() + "_" + cellTiers[i], "inventory"));
        }

        // Register compacting component models for each tier (only 1k-64k have components)
        String[] componentTiers = ItemCompactingComponent.getTierNames();
        for (int i = 0; i < componentTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(COMPACTING_COMPONENT, i,
                new ModelResourceLocation(COMPACTING_COMPONENT.getRegistryName() + "_" + componentTiers[i], "inventory"));
        }

        // Register high-density cell models for each tier
        String[] hdCellTiers = ItemHighDensityCell.getTierNames();
        for (int i = 0; i < hdCellTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(HIGH_DENSITY_CELL, i,
                new ModelResourceLocation(HIGH_DENSITY_CELL.getRegistryName() + "_" + hdCellTiers[i], "inventory"));
        }

        // Register high-density component models for each tier
        String[] hdComponentTiers = ItemHighDensityComponent.getTierNames();
        for (int i = 0; i < hdComponentTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(HIGH_DENSITY_COMPONENT, i,
                new ModelResourceLocation(HIGH_DENSITY_COMPONENT.getRegistryName() + "_" + hdComponentTiers[i], "inventory"));
        }

        // Register high-density compacting cell models for each tier
        String[] hdCompactingCellTiers = ItemHighDensityCompactingCell.getTierNames();
        for (int i = 0; i < hdCompactingCellTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(HIGH_DENSITY_COMPACTING_CELL, i,
                new ModelResourceLocation(HIGH_DENSITY_COMPACTING_CELL.getRegistryName() + "_" + hdCompactingCellTiers[i], "inventory"));
        }

        // Register high-density compacting component models for each tier
        String[] hdCompactingComponentTiers = ItemHighDensityCompactingComponent.getTierNames();
        for (int i = 0; i < hdCompactingComponentTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(HIGH_DENSITY_COMPACTING_COMPONENT, i,
                new ModelResourceLocation(HIGH_DENSITY_COMPACTING_COMPONENT.getRegistryName() + "_" + hdCompactingComponentTiers[i], "inventory"));
        }

        // Register normal storage cell models for each tier (64M-2G)
        String[] normalCellTiers = ItemNormalStorageCell.getTierNames();
        for (int i = 0; i < normalCellTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(NORMAL_STORAGE_CELL, i,
                new ModelResourceLocation(NORMAL_STORAGE_CELL.getRegistryName() + "_" + normalCellTiers[i], "inventory"));
        }

        // Register normal storage component models for each tier (64M-2G)
        String[] normalComponentTiers = ItemNormalStorageComponent.getTierNames();
        for (int i = 0; i < normalComponentTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(NORMAL_STORAGE_COMPONENT, i,
                new ModelResourceLocation(NORMAL_STORAGE_COMPONENT.getRegistryName() + "_" + normalComponentTiers[i], "inventory"));
        }
    }

    @SideOnly(Side.CLIENT)
    private static void registerModel(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
            new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
