package com.cellterminal.items.cells.highdensity;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;


/**
 * Inventory implementation for high-density storage cells.
 * 
 * This inventory handles the internal byte multiplier, ensuring all calculations
 * are overflow-safe. The display shows standard byte values (1k, 4k, etc.)
 * but internally stores vastly more.
 * 
 * Key overflow protection points:
 * - All capacity calculations use multiplyWithOverflowProtection
 * - Storage is tracked in a way that avoids overflow during item operations
 * - Division is preferred over multiplication where possible
 */
public class HighDensityCellInventory implements ICellInventory<IAEItemStack> {

    private static final String NBT_ITEM_COUNT = "itemCount";
    private static final String NBT_ITEM_TYPE = "itemType";
    private static final String NBT_ITEM_COUNT_HI = "itemCountHi";
    private static final int MAX_TYPES = 63;

    private final ItemStack cellStack;
    private final ISaveProvider container;
    private final IStorageChannel<IAEItemStack> channel;
    private final IItemHighDensityCell cellType;

    private final NBTTagCompound tagCompound;

    // Storage tracking - count of stored items (not bytes)
    private long storedItemCount = 0;
    private int storedTypes = 0;

    public HighDensityCellInventory(IItemHighDensityCell cellType, ItemStack cellStack, ISaveProvider container) {
        this.cellStack = cellStack;
        this.container = container;
        this.cellType = cellType;
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        this.tagCompound = Platform.openNbtData(cellStack);

        loadFromNBT();
    }

    private void loadFromNBT() {
        // Load item count (stored as hi/lo for long support)
        if (tagCompound.hasKey(NBT_ITEM_COUNT_HI)) {
            int hi = tagCompound.getInteger(NBT_ITEM_COUNT_HI);
            int lo = tagCompound.getInteger(NBT_ITEM_COUNT);
            storedItemCount = ((long) hi << 32) | (lo & 0xFFFFFFFFL);
        } else if (tagCompound.hasKey(NBT_ITEM_COUNT)) {
            storedItemCount = tagCompound.getLong(NBT_ITEM_COUNT);
        }

        // Count stored types from existing NBT structure
        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);
        storedTypes = 0;
        for (String key : itemsTag.getKeySet()) {
            NBTTagCompound itemTag = itemsTag.getCompoundTag(key);
            if (itemTag.hasKey("Count") || itemTag.hasKey("CountHi")) {
                long count = loadLongFromTag(itemTag, "Count");
                if (count > 0) storedTypes++;
            }
        }
    }

    private long loadLongFromTag(NBTTagCompound tag, String key) {
        if (tag.hasKey(key + "Hi")) {
            int hi = tag.getInteger(key + "Hi");
            int lo = tag.getInteger(key);

            return ((long) hi << 32) | (lo & 0xFFFFFFFFL);
        }

        return tag.getLong(key);
    }

    private void saveLongToTag(NBTTagCompound tag, String key, long value) {
        tag.setInteger(key + "Hi", (int) (value >> 32));
        tag.setInteger(key, (int) value);
    }

    private void saveToNBT() {
        saveLongToTag(tagCompound, NBT_ITEM_COUNT, storedItemCount);
    }

    private void saveChanges() {
        saveToNBT();
        if (container != null) container.saveChanges(this);
    }

    /**
     * Get the total capacity in items (not bytes).
     * This is calculated by: (totalBytes - typeOverhead) * itemsPerByte
     * 
     * We use careful division to avoid overflow.
     */
    private long getTotalItemCapacity() {
        long displayBytes = cellType.getDisplayBytes(cellStack);
        long multiplier = cellType.getByteMultiplier();
        int itemsPerByte = channel.getUnitsPerByte();

        // Calculate type overhead in display bytes, then multiply
        long typeBytesDisplay = storedTypes * getDisplayBytesPerType();
        long availableDisplayBytes = displayBytes - typeBytesDisplay;

        if (availableDisplayBytes <= 0) return 0;

        // availableDisplayBytes * multiplier * itemsPerByte
        // Do this carefully to avoid overflow
        // First: availableDisplayBytes * itemsPerByte (usually safe)
        long itemsAtDisplayScale = multiplyWithOverflowProtection(availableDisplayBytes, itemsPerByte);
        if (itemsAtDisplayScale == Long.MAX_VALUE) return Long.MAX_VALUE;

        // Then multiply by the byte multiplier
        return multiplyWithOverflowProtection(itemsAtDisplayScale, multiplier);
    }

    /**
     * Get bytes per type in display units (before multiplier).
     */
    private long getDisplayBytesPerType() {
        int meta = cellStack.getMetadata();
        // bytesPerType is already multiplied in cellType.getBytesPerType()
        // We need the display version, so we divide by multiplier
        // But it's safer to just use the known ratios
        long[] displayBytesPerType = {8L, 32L, 128L, 512L, 2048L, 8192L, 32768L, 131072L, 524288L, 2097152L, 8388608L};
        if (meta >= 0 && meta < displayBytesPerType.length) return displayBytesPerType[meta];

        return displayBytesPerType[0];
    }

    /**
     * Multiply two longs with overflow protection.
     */
    private static long multiplyWithOverflowProtection(long a, long b) {
        if (a == 0 || b == 0) return 0;
        if (a < 0 || b < 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;

        return a * b;
    }

    /**
     * Add two longs with overflow protection.
     */
    private static long addWithOverflowProtection(long a, long b) {
        if (a < 0 || b < 0) return Math.max(a, b);
        if (a > Long.MAX_VALUE - b) return Long.MAX_VALUE;

        return a + b;
    }

    /**
     * Get the stored item data for a specific item from NBT.
     */
    private long getStoredCount(IAEItemStack item) {
        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);
        String key = getItemKey(item);

        if (!itemsTag.hasKey(key)) return 0;

        return loadLongFromTag(itemsTag.getCompoundTag(key), "Count");
    }

    /**
     * Set the stored count for a specific item in NBT.
     */
    private void setStoredCount(IAEItemStack item, long count) {
        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);
        String key = getItemKey(item);

        if (count <= 0) {
            itemsTag.removeTag(key);
        } else {
            NBTTagCompound itemTag = new NBTTagCompound();
            item.getDefinition().writeToNBT(itemTag);
            saveLongToTag(itemTag, "Count", count);
            itemsTag.setTag(key, itemTag);
        }

        tagCompound.setTag(NBT_ITEM_TYPE, itemsTag);
    }

    /**
     * Generate a unique key for an item stack.
     */
    private String getItemKey(IAEItemStack item) {
        ItemStack def = item.getDefinition();

        return def.getItem().getRegistryName() + "@" + def.getMetadata();
    }

    /**
     * Check if the cell can accept this item (not blacklisted).
     */
    private boolean canAcceptItem(IAEItemStack item) {
        return !cellType.isBlackListed(cellStack, item);
    }

    // =====================
    // ICellInventory implementation
    // =====================

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, appeng.api.networking.security.IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;
        if (!canAcceptItem(input)) return input;

        long existingCount = getStoredCount(input);
        boolean isNewType = existingCount == 0;

        // Check if we can add a new type
        if (isNewType && storedTypes >= MAX_TYPES) return input;

        // Calculate available capacity
        long capacity = getTotalItemCapacity();
        long available = capacity - storedItemCount;

        if (available <= 0) return input;

        long toInsert = Math.min(input.getStackSize(), available);

        if (mode == Actionable.MODULATE) {
            if (isNewType) storedTypes++;

            setStoredCount(input, addWithOverflowProtection(existingCount, toInsert));
            storedItemCount = addWithOverflowProtection(storedItemCount, toInsert);
            saveChanges();
        }

        if (toInsert >= input.getStackSize()) return null;

        IAEItemStack remainder = input.copy();
        remainder.setStackSize(input.getStackSize() - toInsert);

        return remainder;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, appeng.api.networking.security.IActionSource src) {
        if (request == null || request.getStackSize() <= 0) return null;

        long existingCount = getStoredCount(request);
        if (existingCount <= 0) return null;

        long toExtract = Math.min(request.getStackSize(), existingCount);

        if (mode == Actionable.MODULATE) {
            long newCount = existingCount - toExtract;
            setStoredCount(request, newCount);

            if (newCount <= 0) storedTypes = Math.max(0, storedTypes - 1);

            storedItemCount = Math.max(0, storedItemCount - toExtract);
            saveChanges();
        }

        IAEItemStack result = request.copy();
        result.setStackSize(toExtract);

        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);

        for (String key : itemsTag.getKeySet()) {
            NBTTagCompound itemTag = itemsTag.getCompoundTag(key);
            ItemStack stack = new ItemStack(itemTag);
            if (stack.isEmpty()) continue;

            long count = loadLongFromTag(itemTag, "Count");
            if (count <= 0) continue;

            IAEItemStack aeStack = channel.createStack(stack);
            if (aeStack != null) {
                aeStack.setStackSize(count);
                out.add(aeStack);
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
        // Return display bytes per type for AE2 display purposes
        // The actual multiplied value would overflow int
        return (int) Math.min(getDisplayBytesPerType(), Integer.MAX_VALUE);
    }

    @Override
    public boolean canHoldNewItem() {
        return storedTypes < MAX_TYPES && getRemainingItemCount() > 0;
    }

    @Override
    public long getTotalBytes() {
        // Return display bytes for AE2 display
        return cellType.getDisplayBytes(cellStack);
    }

    @Override
    public long getFreeBytes() {
        return getTotalBytes() - getUsedBytes();
    }

    @Override
    public long getTotalItemTypes() {
        return MAX_TYPES;
    }

    @Override
    public long getStoredItemCount() {
        return storedItemCount;
    }

    @Override
    public long getStoredItemTypes() {
        return storedTypes;
    }

    @Override
    public long getRemainingItemTypes() {
        return MAX_TYPES - storedTypes;
    }

    @Override
    public long getUsedBytes() {
        // Calculate used bytes in display scale
        int itemsPerByte = channel.getUnitsPerByte();
        long multiplier = cellType.getByteMultiplier();

        // Items use bytes at: storedItemCount / itemsPerByte / multiplier (display bytes)
        // Plus type overhead: storedTypes * displayBytesPerType

        // To avoid overflow, divide first
        long itemsPerDisplayByte = multiplyWithOverflowProtection(itemsPerByte, multiplier);
        if (itemsPerDisplayByte == 0) itemsPerDisplayByte = 1;

        long usedForItems = (storedItemCount + itemsPerDisplayByte - 1) / itemsPerDisplayByte;
        long usedForTypes = storedTypes * getDisplayBytesPerType();

        return addWithOverflowProtection(usedForItems, usedForTypes);
    }

    @Override
    public long getRemainingItemCount() {
        long capacity = getTotalItemCapacity();
        return Math.max(0, capacity - storedItemCount);
    }

    @Override
    public int getUnusedItemCount() {
        // Fractional items that don't fill a byte (in display scale)
        int itemsPerByte = channel.getUnitsPerByte();
        long multiplier = cellType.getByteMultiplier();
        long itemsPerDisplayByte = multiplyWithOverflowProtection(itemsPerByte, multiplier);

        if (itemsPerDisplayByte == 0) return 0;

        long usedBytes = getUsedBytes() - storedTypes * getDisplayBytesPerType();
        long fullItems = usedBytes * itemsPerDisplayByte;

        return (int) Math.min(fullItems - storedItemCount, Integer.MAX_VALUE);
    }

    @Override
    public int getStatusForCell() {
        if (storedItemCount == 0 && storedTypes == 0) return 4; // Empty
        if (canHoldNewItem()) return 1;                          // Has space for new types
        if (getRemainingItemCount() > 0) return 2;               // Has space for more of existing

        return 3; // Full
    }

    @Override
    public void persist() {
        saveToNBT();
    }
}
