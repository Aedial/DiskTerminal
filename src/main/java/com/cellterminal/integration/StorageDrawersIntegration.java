package com.cellterminal.integration;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.fml.common.Loader;

import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;

import com.cellterminal.config.CellTerminalServerConfig;


/**
 * Integration handler for Storage Drawers mod (and other compatible mods).
 * Provides efficient item querying via IItemRepository capability.
 */
public class StorageDrawersIntegration {

    private static final String MODID = "storagedrawers";
    private static Boolean modLoaded = null;

    @CapabilityInject(IItemRepository.class)
    private static Capability<IItemRepository> ITEM_REPOSITORY_CAPABILITY = null;

    /**
     * Check if Storage Drawers is loaded.
     */
    public static boolean isModLoaded() {
        if (modLoaded == null) {
            boolean loaded = Loader.isModLoaded(MODID);
            if (loaded && CellTerminalServerConfig.isInitialized()) {
                loaded = CellTerminalServerConfig.getInstance().isIntegrationStorageDrawersEnabled();
            }

            modLoaded = loaded;
        }

        return modLoaded;
    }

    /**
     * Try to get contents from a TileEntity using IItemRepository capability.
     * Returns a list of ItemRecordData (item + count pairs) or null if not supported.
     *
     * @param targetTile The tile entity to query
     * @param side The side to query from (can be null for default)
     * @return List of item records, or null if IItemRepository is not available
     */
    public static List<ItemRecordData> tryGetItemRepositoryContents(TileEntity targetTile, EnumFacing side) {
        if (!isModLoaded() || targetTile == null) return null;
        if (ITEM_REPOSITORY_CAPABILITY == null) return null;

        IItemRepository repo = targetTile.getCapability(ITEM_REPOSITORY_CAPABILITY, side);
        if (repo == null) return null;

        NonNullList<IItemRepository.ItemRecord> records = repo.getAllItems();
        if (records == null || records.isEmpty()) return new ArrayList<>();

        List<ItemRecordData> result = new ArrayList<>();
        for (IItemRepository.ItemRecord record : records) {
            if (record.itemPrototype.isEmpty()) continue;

            result.add(new ItemRecordData(record.itemPrototype.copy(), record.count));
        }

        return result;
    }

    /**
     * Check if a TileEntity supports IItemRepository.
     *
     * @param targetTile The tile entity to check
     * @param side The side to check from (can be null)
     * @return true if IItemRepository is supported
     */
    public static boolean hasItemRepository(TileEntity targetTile, EnumFacing side) {
        if (!isModLoaded() || targetTile == null) return false;
        if (ITEM_REPOSITORY_CAPABILITY == null) return false;

        IItemRepository repo = targetTile.getCapability(ITEM_REPOSITORY_CAPABILITY, side);

        return repo != null;
    }

    /**
     * Simple data class to hold item + count pairs.
     */
    public static class ItemRecordData {
        public final ItemStack itemPrototype;
        public final int count;

        public ItemRecordData(ItemStack itemPrototype, int count) {
            this.itemPrototype = itemPrototype;
            this.count = count;
        }
    }
}
