package com.cellterminal.items.cells.highdensity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;

import com.cellterminal.items.cells.compacting.CompactingHelper;


/**
 * Inventory implementation for high-density compacting storage cells.
 * 
 * Combines the compacting functionality (compression chains) with the
 * high-density byte multiplier for massive storage.
 * 
 * All calculations are overflow-protected. Due to base unit multiplication,
 * HD compacting cells are limited to 16M tier maximum.
 */
public class HighDensityCompactingCellInventory implements ICellInventory<IAEItemStack> {

    private static final String NBT_STORED_BASE_UNITS = "storedBaseUnits";
    private static final String NBT_CONV_RATES = "convRates";
    private static final String NBT_PROTO_ITEMS = "protoItems";
    private static final String NBT_MAIN_TIER = "mainTier";
    private static final String NBT_CACHED_PARTITION = "cachedPartition";

    private static final int MAX_TIERS = 3;

    private ItemStack cachedPartitionItem = ItemStack.EMPTY;

    private final ItemStack cellStack;
    private final ISaveProvider container;
    private final IStorageChannel<IAEItemStack> channel;
    private final IItemHighDensityCompactingCell cellType;

    private final NBTTagCompound tagCompound;

    private final ItemStack[] protoStack = new ItemStack[MAX_TIERS];
    private final int[] convRate = new int[MAX_TIERS];

    private long storedBaseUnits = 0;
    private int mainTier = -1;

    public HighDensityCompactingCellInventory(IItemHighDensityCompactingCell cellType, ItemStack cellStack, ISaveProvider container) {
        this.cellStack = cellStack;
        this.container = container;
        this.cellType = cellType;
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);

        this.tagCompound = Platform.openNbtData(cellStack);

        for (int i = 0; i < MAX_TIERS; i++) {
            protoStack[i] = ItemStack.EMPTY;
            convRate[i] = 0;
        }

        loadFromNBT();
    }

    @Nullable
    private static World getWorldFromSource(@Nullable IActionSource src) {
        if (src == null) return null;
        if (src.player().isPresent()) return src.player().get().world;
        if (src.machine().isPresent()) {
            IActionHost host = src.machine().get();
            IGridNode node = host.getActionableNode();
            if (node != null) return node.getWorld();
        }

        return null;
    }

    @Nullable
    private static IGrid getGridFromSource(@Nullable IActionSource src) {
        if (src == null) return null;
        if (src.machine().isPresent()) {
            IActionHost host = src.machine().get();
            IGridNode node = host.getActionableNode();
            if (node != null) return node.getGrid();
        }

        return null;
    }

    private void notifyGridOfAllTierChanges(@Nullable IActionSource src, long oldBaseUnits, int operatedSlot) {
        IGrid grid = getGridFromSource(src);
        if (grid == null) return;

        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        if (storageGrid == null) return;

        List<IAEItemStack> changes = new ArrayList<>();

        for (int i = 0; i < MAX_TIERS; i++) {
            if (i == operatedSlot) continue;
            if (protoStack[i].isEmpty() || convRate[i] <= 0) continue;

            long oldCount = oldBaseUnits / convRate[i];
            long newCount = storedBaseUnits / convRate[i];
            long delta = newCount - oldCount;

            if (delta == 0) continue;

            IAEItemStack stack = channel.createStack(protoStack[i]);
            if (stack != null) {
                stack.setStackSize(delta);
                changes.add(stack);
            }
        }

        if (!changes.isEmpty()) storageGrid.postAlterationOfStoredItems(channel, changes, src);
    }

    private void loadFromNBT() {
        if (tagCompound.hasKey(NBT_STORED_BASE_UNITS + "_hi")) {
            storedBaseUnits = loadLong(tagCompound, NBT_STORED_BASE_UNITS);
        }

        if (tagCompound.hasKey(NBT_CONV_RATES)) {
            int[] rates = tagCompound.getIntArray(NBT_CONV_RATES);
            for (int i = 0; i < Math.min(rates.length, MAX_TIERS); i++) convRate[i] = rates[i];
        }

        if (tagCompound.hasKey(NBT_PROTO_ITEMS)) {
            NBTTagCompound protoNbt = tagCompound.getCompoundTag(NBT_PROTO_ITEMS);
            for (int i = 0; i < MAX_TIERS; i++) {
                if (protoNbt.hasKey("item" + i)) {
                    protoStack[i] = new ItemStack(protoNbt.getCompoundTag("item" + i));
                }
            }
        }

        if (tagCompound.hasKey(NBT_MAIN_TIER)) mainTier = tagCompound.getInteger(NBT_MAIN_TIER);

        if (tagCompound.hasKey(NBT_CACHED_PARTITION)) {
            cachedPartitionItem = new ItemStack(tagCompound.getCompoundTag(NBT_CACHED_PARTITION));
        }
    }

    private static long loadLong(NBTTagCompound tag, String key) {
        int hi = tag.getInteger(key + "_hi");
        int lo = tag.getInteger(key + "_lo");

        return ((long) hi << 32) | (lo & 0xFFFFFFFFL);
    }

    private static void saveLong(NBTTagCompound tag, String key, long value) {
        tag.setInteger(key + "_hi", (int) (value >> 32));
        tag.setInteger(key + "_lo", (int) value);
    }

    /**
     * Multiply with overflow protection - returns Long.MAX_VALUE on overflow.
     */
    private static long multiplyWithOverflowProtection(long a, long b) {
        if (a == 0 || b == 0) return 0;
        if (a < 0 || b < 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;

        return a * b;
    }

    /**
     * Add with overflow protection - returns Long.MAX_VALUE on overflow.
     */
    private static long addWithOverflowProtection(long a, long b) {
        if (a < 0 || b < 0) return Math.max(a, b);
        if (a > Long.MAX_VALUE - b) return Long.MAX_VALUE;

        return a + b;
    }

    private void saveToNBT() {
        saveLong(tagCompound, NBT_STORED_BASE_UNITS, storedBaseUnits);
        tagCompound.setInteger(NBT_MAIN_TIER, mainTier);

        if (!isCompressionChainEmpty()) {
            tagCompound.setIntArray(NBT_CONV_RATES, convRate);

            NBTTagCompound protoNbt = new NBTTagCompound();
            for (int i = 0; i < MAX_TIERS; i++) {
                if (!protoStack[i].isEmpty()) {
                    NBTTagCompound itemNbt = new NBTTagCompound();
                    protoStack[i].writeToNBT(itemNbt);
                    protoNbt.setTag("item" + i, itemNbt);
                }
            }
            tagCompound.setTag(NBT_PROTO_ITEMS, protoNbt);

            if (!cachedPartitionItem.isEmpty()) {
                NBTTagCompound partNbt = new NBTTagCompound();
                cachedPartitionItem.writeToNBT(partNbt);
                tagCompound.setTag(NBT_CACHED_PARTITION, partNbt);
            }
        } else if (storedBaseUnits == 0 && !hasPartition()) {
            tagCompound.removeTag(NBT_CONV_RATES);
            tagCompound.removeTag(NBT_PROTO_ITEMS);
            tagCompound.removeTag(NBT_CACHED_PARTITION);
        }
    }

    private void saveChanges() {
        saveToNBT();
        if (container != null) container.saveChanges(this);
    }

    private void initializeCompressionChain(@Nonnull ItemStack inputItem, @Nullable World world) {
        if (world == null) {
            protoStack[0] = inputItem.copy();
            protoStack[0].setCount(1);
            convRate[0] = 1;
            mainTier = 0;

            return;
        }

        CompactingHelper helper = new CompactingHelper(world);
        CompactingHelper.CompressionChain chain = helper.getCompressionChain(inputItem);

        for (int i = 0; i < MAX_TIERS; i++) {
            protoStack[i] = chain.getStack(i);
            convRate[i] = chain.getRate(i);
        }

        mainTier = 0;
        for (int i = 0; i < MAX_TIERS; i++) {
            if (areItemsEqual(protoStack[i], inputItem)) {
                mainTier = i;
                break;
            }
        }
    }

    private int getSlotForItem(@Nonnull IAEItemStack stack) {
        ItemStack definition = stack.getDefinition();

        for (int i = 0; i < MAX_TIERS; i++) {
            if (areItemsEqual(protoStack[i], definition)) return i;
        }

        return -1;
    }

    private int canAcceptItem(@Nonnull IAEItemStack stack) {
        if (!hasPartition()) return -1;
        if (!isAllowedByPartition(stack)) return -1;
        if (isCompressionChainEmpty()) return 0;

        return getSlotForItem(stack);
    }

    private boolean isCompressionChainEmpty() {
        for (int i = 0; i < MAX_TIERS; i++) {
            if (!protoStack[i].isEmpty()) return false;
        }

        return true;
    }

    public boolean isChainInitialized() {
        return !isCompressionChainEmpty();
    }

    public boolean hasStoredItems() {
        return storedBaseUnits > 0;
    }

    public boolean isInCompressionChain(@Nonnull IAEItemStack stack) {
        if (stack == null) return false;

        ItemStack definition = stack.getDefinition();

        for (int i = 0; i < MAX_TIERS; i++) {
            if (areItemsEqual(protoStack[i], definition)) return true;
        }

        return false;
    }

    public boolean hasPartition() {
        IItemHandler configInv = getConfigInventory();
        if (configInv == null) return false;

        for (int i = 0; i < configInv.getSlots(); i++) {
            if (!configInv.getStackInSlot(i).isEmpty()) return true;
        }

        return false;
    }

    private boolean hasPartitionChanged() {
        ItemStack currentPartition = getFirstPartitionedItem();

        if ((currentPartition == null || currentPartition.isEmpty()) && cachedPartitionItem.isEmpty()) {
            return false;
        }

        if (currentPartition == null || currentPartition.isEmpty() || cachedPartitionItem.isEmpty()) {
            return true;
        }

        return !areItemsEqual(currentPartition, cachedPartitionItem);
    }

    public void initializeChainFromPartition(@Nullable World world) {
        updateCompressionChainIfNeeded(world);
    }

    public void initializeChainForItem(@Nonnull ItemStack partitionItem, @Nullable World world) {
        if (partitionItem.isEmpty()) return;
        if (storedBaseUnits > 0) return;

        reset();
        initializeCompressionChain(partitionItem, world);
        cachedPartitionItem = partitionItem.copy();
        cachedPartitionItem.setCount(1);
        saveToNBT();
    }

    private void updateCompressionChainIfNeeded(@Nullable World world) {
        boolean needsInitialization = hasPartition() && isCompressionChainEmpty();

        if (!needsInitialization && !hasPartitionChanged()) return;

        ItemStack currentPartition = getFirstPartitionedItem();

        if (storedBaseUnits > 0) {
            revertPartitionToCached();

            return;
        }

        if (currentPartition == null || currentPartition.isEmpty()) {
            reset();
            cachedPartitionItem = ItemStack.EMPTY;
            saveChanges();

            return;
        }

        reset();
        initializeCompressionChain(currentPartition, world);
        cachedPartitionItem = currentPartition.copy();
        cachedPartitionItem.setCount(1);
        saveChanges();
    }

    private void revertPartitionToCached() {
        if (cachedPartitionItem.isEmpty()) return;

        IItemHandler configInv = getConfigInventory();
        if (!(configInv instanceof IItemHandlerModifiable)) return;

        IItemHandlerModifiable modifiable = (IItemHandlerModifiable) configInv;

        modifiable.setStackInSlot(0, cachedPartitionItem.copy());
        for (int i = 1; i < modifiable.getSlots(); i++) modifiable.setStackInSlot(i, ItemStack.EMPTY);
    }

    private boolean isAllowedByPartition(@Nonnull IAEItemStack stack) {
        IItemHandler configInv = getConfigInventory();
        if (configInv == null) return false;

        ItemStack definition = stack.getDefinition();

        for (int i = 0; i < MAX_TIERS; i++) {
            if (areItemsEqual(protoStack[i], definition)) return true;
        }

        for (int i = 0; i < configInv.getSlots(); i++) {
            ItemStack partItem = configInv.getStackInSlot(i);
            if (!partItem.isEmpty() && areItemsEqual(partItem, definition)) return true;
        }

        return false;
    }

    @Nullable
    private ItemStack getFirstPartitionedItem() {
        IItemHandler configInv = getConfigInventory();
        if (configInv == null) return null;

        for (int i = 0; i < configInv.getSlots(); i++) {
            ItemStack partItem = configInv.getStackInSlot(i);
            if (!partItem.isEmpty()) return partItem.copy();
        }

        return null;
    }

    private long getStoredInMainTier() {
        if (mainTier < 0 || mainTier >= MAX_TIERS) return 0;
        if (convRate[mainTier] <= 0) return 0;

        return storedBaseUnits / convRate[mainTier];
    }

    private long getStoredInMainTierCeiling() {
        if (mainTier < 0 || mainTier >= MAX_TIERS) return 0;
        if (convRate[mainTier] <= 0) return 0;

        return (storedBaseUnits + convRate[mainTier] - 1) / convRate[mainTier];
    }

    /**
     * Get the maximum capacity in base units.
     * Uses overflow-protected multiplication for HD multiplier.
     */
    private long getMaxCapacityInBaseUnits() {
        // Get actual total bytes (display * multiplier) - already multiplied in cellType
        long totalBytes = cellType.getBytes(cellStack);
        long typeBytes = isCompressionChainEmpty() ? 0 : cellType.getBytesPerType(cellStack);
        long availableBytes = totalBytes - typeBytes;

        if (availableBytes <= 0) return 0;
        if (mainTier < 0 || mainTier >= MAX_TIERS || convRate[mainTier] <= 0) return 0;

        int itemsPerByte = channel.getUnitsPerByte();

        // availableBytes * itemsPerByte - overflow protected
        long maxMainTierItems = multiplyWithOverflowProtection(availableBytes, itemsPerByte);
        if (maxMainTierItems == Long.MAX_VALUE) return Long.MAX_VALUE;

        // maxMainTierItems * convRate[mainTier] - overflow protected
        return multiplyWithOverflowProtection(maxMainTierItems, convRate[mainTier]);
    }

    private long getRemainingCapacityInBaseUnits() {
        long max = getMaxCapacityInBaseUnits();
        if (max == Long.MAX_VALUE) return Long.MAX_VALUE;

        return Math.max(0, max - storedBaseUnits);
    }

    @Nonnull
    public ItemStack getPartitionedItem() {
        if (mainTier >= 0 && mainTier < MAX_TIERS && !protoStack[mainTier].isEmpty()) {
            return protoStack[mainTier];
        }

        ItemStack firstPart = getFirstPartitionedItem();

        return firstPart != null ? firstPart : ItemStack.EMPTY;
    }

    @Nonnull
    public ItemStack getHigherTierItem() {
        if (mainTier <= 0) return ItemStack.EMPTY;

        for (int i = mainTier - 1; i >= 0; i--) {
            if (!protoStack[i].isEmpty()) return protoStack[i];
        }

        return ItemStack.EMPTY;
    }

    @Nonnull
    public ItemStack getLowerTierItem() {
        if (mainTier < 0) return ItemStack.EMPTY;

        for (int i = mainTier + 1; i < MAX_TIERS; i++) {
            if (!protoStack[i].isEmpty()) return protoStack[i];
        }

        return ItemStack.EMPTY;
    }

    private void reset() {
        for (int i = 0; i < MAX_TIERS; i++) {
            protoStack[i] = ItemStack.EMPTY;
            convRate[i] = 0;
        }
        storedBaseUnits = 0;
        mainTier = -1;
    }

    /**
     * Check if the cell has an overflow card installed.
     * This upgrade causes excess items to be voided instead of rejected.
     */
    private boolean hasOverflowCard() {
        IItemHandler upgrades = getUpgradesInventory();
        if (upgrades == null) return false;

        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof com.cellterminal.items.ItemOverflowCard) return true;
        }

        return false;
    }

    // =====================
    // ICellInventory implementation
    // =====================

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;

        if (!hasPartition()) return input;

        World world = getWorldFromSource(src);
        updateCompressionChainIfNeeded(world);

        int slot = canAcceptItem(input);
        if (slot < 0) return input;
        if (convRate[slot] <= 0) return input;

        long inputCount = input.getStackSize();
        long inputInBaseUnits = multiplyWithOverflowProtection(inputCount, convRate[slot]);
        long remainingCapacityBaseUnits = getRemainingCapacityInBaseUnits();
        long canInsertBaseUnits = Math.min(inputInBaseUnits, remainingCapacityBaseUnits);
        long canInsert = canInsertBaseUnits / convRate[slot];

        if (canInsert <= 0) {
            if (hasOverflowCard()) return null;

            return input;
        }

        long actualBaseUnits = canInsert * convRate[slot];

        if (mode == Actionable.MODULATE) {
            long oldBaseUnits = storedBaseUnits;

            storedBaseUnits = addWithOverflowProtection(storedBaseUnits, actualBaseUnits);
            saveChanges();

            notifyGridOfAllTierChanges(src, oldBaseUnits, slot);
        }

        if (canInsert >= inputCount) return null;
        if (hasOverflowCard()) return null;

        IAEItemStack remainder = input.copy();
        remainder.setStackSize(inputCount - canInsert);

        return remainder;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource src) {
        if (request == null || request.getStackSize() <= 0) return null;

        updateCompressionChainIfNeeded(getWorldFromSource(src));

        int slot = getSlotForItem(request);
        if (slot < 0) return null;
        if (convRate[slot] <= 0) return null;

        long requestedCount = request.getStackSize();
        long availableInThisTier = storedBaseUnits / convRate[slot];
        long toExtract = Math.min(requestedCount, availableInThisTier);

        if (toExtract <= 0) return null;

        if (mode == Actionable.MODULATE) {
            long oldBaseUnits = storedBaseUnits;

            storedBaseUnits -= toExtract * convRate[slot];
            saveChanges();

            notifyGridOfAllTierChanges(src, oldBaseUnits, slot);
        }

        IAEItemStack result = request.copy();
        result.setStackSize(toExtract);

        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        if (storedBaseUnits <= 0) return out;

        for (int i = 0; i < MAX_TIERS; i++) {
            if (protoStack[i].isEmpty() || convRate[i] <= 0) continue;

            long availableCount = storedBaseUnits / convRate[i];
            if (availableCount <= 0) continue;

            IAEItemStack stack = channel.createStack(protoStack[i]);
            if (stack != null) {
                stack.setStackSize(availableCount);
                out.add(stack);
            }
        }

        return out;
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return channel;
    }

    @Override
    public ItemStack getItemStack() {
        return cellStack;
    }

    @Override
    public double getIdleDrain() {
        return cellType.getIdleDrain();
    }

    @Override
    public FuzzyMode getFuzzyMode() {
        return cellType.getFuzzyMode(cellStack);
    }

    @Override
    public IItemHandler getConfigInventory() {
        return cellType.getConfigInventory(cellStack);
    }

    @Override
    public IItemHandler getUpgradesInventory() {
        return cellType.getUpgradesInventory(cellStack);
    }

    @Override
    public int getBytesPerType() {
        // Return display bytes per type for UI (actual would overflow)
        int meta = cellStack.getMetadata();
        long[] displayBpt = {8L, 32L, 128L, 512L, 2048L, 8192L, 32768L, 131072L};
        long val = (meta >= 0 && meta < displayBpt.length) ? displayBpt[meta] : displayBpt[0];

        return (int) Math.min(val, Integer.MAX_VALUE);
    }

    @Override
    public boolean canHoldNewItem() {
        return isCompressionChainEmpty() && getRemainingItemCount() > 0;
    }

    @Override
    public long getTotalBytes() {
        // Return display bytes for UI
        return cellType.getDisplayBytes(cellStack);
    }

    @Override
    public long getFreeBytes() {
        return getTotalBytes() - getUsedBytes();
    }

    @Override
    public long getTotalItemTypes() {
        return 1;
    }

    @Override
    public long getStoredItemCount() {
        return getStoredInMainTier();
    }

    @Override
    public long getStoredItemTypes() {
        return isCompressionChainEmpty() ? 0 : 1;
    }

    @Override
    public long getRemainingItemTypes() {
        return isCompressionChainEmpty() ? 1 : 0;
    }

    @Override
    public long getUsedBytes() {
        int itemsPerByte = channel.getUnitsPerByte();
        long multiplier = cellType.getByteMultiplier();

        long storedItemsCeiling = getStoredInMainTierCeiling();

        // Items per display byte = itemsPerByte * multiplier
        long itemsPerDisplayByte = multiplyWithOverflowProtection(itemsPerByte, multiplier);
        if (itemsPerDisplayByte == 0) itemsPerDisplayByte = 1;

        // usedDisplayBytes = storedItemsCeiling / itemsPerDisplayByte (ceiling)
        long usedForItems = (storedItemsCeiling + itemsPerDisplayByte - 1) / itemsPerDisplayByte;

        long typeBytes = isCompressionChainEmpty() ? 0 : getBytesPerType();

        return typeBytes + usedForItems;
    }

    @Override
    public long getRemainingItemCount() {
        long remainingBaseUnits = getRemainingCapacityInBaseUnits();
        if (mainTier < 0 || convRate[mainTier] <= 0) return 0;

        return remainingBaseUnits / convRate[mainTier];
    }

    @Override
    public int getUnusedItemCount() {
        int itemsPerByte = channel.getUnitsPerByte();
        long multiplier = cellType.getByteMultiplier();
        long storedCeiling = getStoredInMainTierCeiling();

        long itemsPerDisplayByte = multiplyWithOverflowProtection(itemsPerByte, multiplier);
        if (itemsPerDisplayByte == 0) return 0;

        long usedBytes = getUsedBytes() - (isCompressionChainEmpty() ? 0 : getBytesPerType());
        long fullItems = usedBytes * itemsPerDisplayByte;

        return (int) Math.min(fullItems - storedCeiling, Integer.MAX_VALUE);
    }

    @Override
    public int getStatusForCell() {
        if (getUsedBytes() == 0) return 4;
        if (canHoldNewItem()) return 1;
        if (getRemainingItemCount() > 0) return 2;

        return 3;
    }

    @Override
    public void persist() {
        saveToNBT();
    }

    private static boolean areItemsEqual(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;

        return a.getItem() == b.getItem() && a.getMetadata() == b.getMetadata()
            && ItemStack.areItemStackTagsEqual(a, b);
    }
}
