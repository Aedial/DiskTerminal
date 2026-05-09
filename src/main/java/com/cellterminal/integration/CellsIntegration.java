package com.cellterminal.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.parts.PartItemStack;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.parts.AEBasePart;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import com.cells.api.FilterHostUtil;
import com.cells.api.IFilterHost;
import com.cells.api.IInterfaceHost;
import com.cells.api.ResourcePreviewEntry;
import com.cells.api.ResourceType;

import com.cellterminal.client.StorageType;


/**
 * Shared helpers for CELLS-facing runtime integrations.
 */
public final class CellsIntegration {

    private static final String CELLS_MODID = "cells";
    private static final String MEKENG_MODID = "mekeng";
    private static final String THAUMICENERGISTICS_MODID = "thaumicenergistics";

    private CellsIntegration() {}

    public static boolean isCellsLoaded() {
        return Loader.isModLoaded(CELLS_MODID);
    }

    @Nonnull
    public static StorageType toStorageType(@Nullable ResourceType type) {
        if (type == null) return StorageType.ITEM;

        switch (type) {
            case FLUID:
                return StorageType.FLUID;
            case GAS:
                return StorageType.GAS;
            case ESSENTIA:
                return StorageType.ESSENTIA;
            case ITEM:
            default:
                return StorageType.ITEM;
        }
    }

    @Nonnull
    public static ItemStack normalizeStack(@Nonnull ItemStack stack) {
        return FilterHostUtil.normalizeFilter(stack);
    }

    @Nullable
    public static TileEntity getHostTile(@Nullable Object host) {
        if (host instanceof TileEntity) return (TileEntity) host;

        if (host instanceof AEBasePart) {
            AEBasePart part = (AEBasePart) host;
            return part.getHost() != null ? part.getHost().getTile() : null;
        }

        return null;
    }

    @Nonnull
    public static ItemStack getPartDisplayStack(@Nullable Object part) {
        if (!(part instanceof AEBasePart)) return ItemStack.EMPTY;

        ItemStack stack = ((AEBasePart) part).getItemStack(PartItemStack.PICK);
        return normalizeStack(stack);
    }

    @Nonnull
    public static ItemStack getTileDisplayStack(@Nullable TileEntity tile) {
        if (tile == null || tile.getWorld() == null) return ItemStack.EMPTY;
        return getBlockDisplayStack(tile.getWorld(), tile.getPos());
    }

    @Nonnull
    public static ItemStack getHostDisplayStack(@Nullable Object host) {
        ItemStack partStack = getPartDisplayStack(host);
        if (!partStack.isEmpty()) return partStack;

        return getTileDisplayStack(getHostTile(host));
    }

    @Nonnull
    public static ItemStack getBlockDisplayStack(@Nullable World world, @Nullable BlockPos pos) {
        if (world == null || pos == null) return ItemStack.EMPTY;

        IBlockState state = world.getBlockState(pos);

        try {
            return state.getBlock().getPickBlock(
                state,
                new RayTraceResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), EnumFacing.UP, pos),
                world,
                pos,
                null
            );
        } catch (Exception e) {
            return new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));
        }
    }

    @Nonnull
    public static NBTTagList createFilterNBT(@Nonnull IFilterHost host) {
        NBTTagList filterList = new NBTTagList();
        int slotCount = Math.max(0, host.getFilterSlots());

        for (int slot = 0; slot < slotCount; slot++) {
            NBTTagCompound filterNbt = new NBTTagCompound();
            ItemStack filter = normalizeStack(host.getFilter(slot));
            if (!filter.isEmpty()) filter.writeToNBT(filterNbt);
            filterList.appendTag(filterNbt);
        }

        return filterList;
    }

    @Nonnull
    public static NBTTagList createPreviewNBT(@Nullable List<ResourcePreviewEntry> entries,
                                              int limit,
                                              @Nullable Collection<ResourceType> allowedTypes) {
        NBTTagList previewList = new NBTTagList();
        if (entries == null || entries.isEmpty()) return previewList;

        Collection<ResourceType> safeAllowed = allowedTypes != null ? allowedTypes : Collections.emptyList();
        boolean filterByType = !safeAllowed.isEmpty();
        int written = 0;

        for (ResourcePreviewEntry entry : entries) {
            if (entry == null) continue;
            if (filterByType && !safeAllowed.contains(entry.getResourceType())) continue;

            ItemStack displayStack = normalizeStack(entry.getDisplayStack());
            if (displayStack.isEmpty()) continue;

            NBTTagCompound stackNbt = new NBTTagCompound();
            displayStack.writeToNBT(stackNbt);
            stackNbt.setLong("Cnt", Math.max(0, entry.getAmount()));
            previewList.appendTag(stackNbt);

            written++;
            if (limit > 0 && written >= limit) break;
        }

        return previewList;
    }

    /**
     * CELLS interfaces can expose multiple adjacent facings through one logical host.
     * Merge those previews so callers see the shared host instead of one side at a time.
     */
    @Nonnull
    public static List<ResourcePreviewEntry> collectInterfacePreviewEntries(@Nonnull IInterfaceHost host, int limit) {
        List<ResourcePreviewEntry> previewEntries = new ArrayList<>();
        if (host == null) return previewEntries;

        Collection<EnumFacing> targetFacings = host.getTargetFacings();
        if (targetFacings == null || targetFacings.isEmpty()) {
            mergePreviewEntries(previewEntries, host.getPreviewEntries(limit), limit);
            return previewEntries;
        }

        int perFacingLimit = limit > 0 ? limit : Integer.MAX_VALUE;
        for (EnumFacing facing : targetFacings) {
            if (facing == null) continue;

            mergePreviewEntries(previewEntries, host.getPreviewEntries(facing, perFacingLimit), limit);
        }

        return previewEntries;
    }

    private static void mergePreviewEntries(List<ResourcePreviewEntry> mergedEntries,
                                            @Nullable List<ResourcePreviewEntry> incomingEntries,
                                            int limit) {
        if (incomingEntries == null || incomingEntries.isEmpty()) return;

        for (ResourcePreviewEntry entry : incomingEntries) {
            if (entry == null || entry.getAmount() <= 0) continue;

            ItemStack normalized = normalizeStack(entry.getDisplayStack());
            if (normalized.isEmpty()) continue;

            boolean merged = false;
            for (int index = 0; index < mergedEntries.size(); index++) {
                ResourcePreviewEntry existing = mergedEntries.get(index);
                if (existing.getResourceType() != entry.getResourceType()) continue;
                if (!FilterHostUtil.matchesFilter(existing.getDisplayStack(), normalized)) continue;

                mergedEntries.set(index, new ResourcePreviewEntry(
                    entry.getResourceType(),
                    normalized,
                    existing.getAmount() + entry.getAmount()
                ));
                merged = true;
                break;
            }

            if (merged) continue;
            if (limit > 0 && mergedEntries.size() >= limit) continue;

            mergedEntries.add(new ResourcePreviewEntry(entry.getResourceType(), normalized, entry.getAmount()));
        }
    }

    @Nonnull
    public static List<ResourcePreviewEntry> collectGridPreviewEntries(@Nullable IGrid grid, int limit) {
        List<ResourcePreviewEntry> previewEntries = new ArrayList<>();
        if (grid == null) return previewEntries;

        IStorageGrid storageGrid;
        try {
            storageGrid = grid.getCache(IStorageGrid.class);
        } catch (Exception e) {
            return previewEntries;
        }

        if (storageGrid == null) return previewEntries;

        collectItemGridPreviewEntries(storageGrid, previewEntries, limit);
        collectFluidGridPreviewEntries(storageGrid, previewEntries, limit);
        collectGasGridPreviewEntries(storageGrid, previewEntries, limit);
        collectEssentiaGridPreviewEntries(storageGrid, previewEntries, limit);

        return previewEntries;
    }

    private static void collectItemGridPreviewEntries(IStorageGrid storageGrid,
                                                      List<ResourcePreviewEntry> previewEntries,
                                                      int limit) {
        try {
            IItemStorageChannel itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            IMEMonitor<IAEItemStack> itemMonitor = storageGrid.getInventory(itemChannel);
            if (itemMonitor == null) return;

            IItemList<IAEItemStack> storageList = itemMonitor.getStorageList();
            for (IAEItemStack aeStack : storageList) {
                if (limit > 0 && previewEntries.size() >= limit) return;
                if (aeStack.getStackSize() <= 0) continue;

                ItemStack stack = aeStack.createItemStack();
                stack.setCount(1);
                previewEntries.add(new ResourcePreviewEntry(ResourceType.ITEM, stack, aeStack.getStackSize()));
            }
        } catch (Exception e) {
            // Ignore missing or unavailable item channels.
        }
    }

    private static void collectFluidGridPreviewEntries(IStorageGrid storageGrid,
                                                       List<ResourcePreviewEntry> previewEntries,
                                                       int limit) {
        try {
            IFluidStorageChannel fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            IMEMonitor<IAEFluidStack> fluidMonitor = storageGrid.getInventory(fluidChannel);
            if (fluidMonitor == null) return;

            IItemList<IAEFluidStack> storageList = fluidMonitor.getStorageList();
            for (IAEFluidStack aeFluid : storageList) {
                if (limit > 0 && previewEntries.size() >= limit) return;
                if (aeFluid.getStackSize() <= 0) continue;

                ItemStack fluidRep = aeFluid.asItemStackRepresentation();
                if (fluidRep.isEmpty()) continue;

                previewEntries.add(new ResourcePreviewEntry(ResourceType.FLUID, fluidRep, aeFluid.getStackSize()));
            }
        } catch (Exception e) {
            // Ignore missing or unavailable fluid channels.
        }
    }

    private static void collectGasGridPreviewEntries(IStorageGrid storageGrid,
                                                     List<ResourcePreviewEntry> previewEntries,
                                                     int limit) {
        if (!MekanismEnergisticsIntegration.isModLoaded()) return;

        collectGasGridPreviewEntriesInternal(storageGrid, previewEntries, limit);
    }

    @Optional.Method(modid = MEKENG_MODID)
    private static void collectGasGridPreviewEntriesInternal(IStorageGrid storageGrid,
                                                             List<ResourcePreviewEntry> previewEntries,
                                                             int limit) {
        try {
            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);
            IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> gasMonitor = storageGrid.getInventory(gasChannel);
            if (gasMonitor == null) return;

            IItemList<com.mekeng.github.common.me.data.IAEGasStack> storageList = gasMonitor.getStorageList();
            for (com.mekeng.github.common.me.data.IAEGasStack aeGas : storageList) {
                if (limit > 0 && previewEntries.size() >= limit) return;
                if (aeGas == null || aeGas.getStackSize() <= 0) continue;

                ItemStack gasRep = aeGas.asItemStackRepresentation();
                if (gasRep.isEmpty()) continue;

                previewEntries.add(new ResourcePreviewEntry(ResourceType.GAS, gasRep, aeGas.getStackSize()));
            }
        } catch (Exception e) {
            // Ignore missing or unavailable gas channels.
        }
    }

    private static void collectEssentiaGridPreviewEntries(IStorageGrid storageGrid,
                                                          List<ResourcePreviewEntry> previewEntries,
                                                          int limit) {
        if (!ThaumicEnergisticsIntegration.isModLoaded()) return;

        collectEssentiaGridPreviewEntriesInternal(storageGrid, previewEntries, limit);
    }

    @Optional.Method(modid = THAUMICENERGISTICS_MODID)
    private static void collectEssentiaGridPreviewEntriesInternal(IStorageGrid storageGrid,
                                                                  List<ResourcePreviewEntry> previewEntries,
                                                                  int limit) {
        try {
            IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaChannel =
                AEApi.instance().storage().getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);
            IMEMonitor<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaMonitor = storageGrid.getInventory(essentiaChannel);
            if (essentiaMonitor == null) return;

            IItemList<thaumicenergistics.api.storage.IAEEssentiaStack> storageList = essentiaMonitor.getStorageList();
            for (thaumicenergistics.api.storage.IAEEssentiaStack aeEssentia : storageList) {
                if (limit > 0 && previewEntries.size() >= limit) return;
                if (aeEssentia == null || aeEssentia.getStackSize() <= 0) continue;

                ItemStack essentiaRep = aeEssentia.asItemStackRepresentation();
                if (essentiaRep.isEmpty()) continue;

                previewEntries.add(new ResourcePreviewEntry(ResourceType.ESSENTIA, essentiaRep, aeEssentia.getStackSize()));
            }
        } catch (Exception e) {
            // Ignore missing or unavailable essentia channels.
        }
    }

    public static boolean hasPartition(@Nonnull IFilterHost host) {
        int slotCount = Math.max(0, host.getFilterSlots());
        for (int slot = 0; slot < slotCount; slot++) {
            if (!host.getFilter(slot).isEmpty()) return true;
        }

        return false;
    }
}