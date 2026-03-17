package com.cellterminal.client;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Identifies the type of storage channel a cell, storage bus, or subnet connection uses.
 * <p>
 * Each value maps to a specific AE2 storage channel:
 * <ul>
 *   <li>{@link #ITEM} - {@code IItemStorageChannel} (vanilla items)</li>
 *   <li>{@link #FLUID} - {@code IFluidStorageChannel} (Forge fluids)</li>
 *   <li>{@link #ESSENTIA} - {@code IEssentiaStorageChannel} (Thaumic Energistics)</li>
 *   <li>{@link #GAS} - Reserved for future gas storage support (Mekanism)</li>
 * </ul>
 * <p>
 * Serialized as an integer ordinal in NBT under the key {@code "storageType"}.
 */
public enum StorageType {
    ITEM,
    FLUID,
    ESSENTIA,
    GAS;

    /** NBT key used for serialization. */
    public static final String NBT_KEY = "storageType";

    /**
     * Deserialize from NBT ordinal.
     *
     * @param nbt The NBT compound to read from
     * @return The deserialized StorageType
     */
    public static StorageType fromNBT(NBTTagCompound nbt) {
        if (nbt.hasKey(NBT_KEY)) {
            int ordinal = nbt.getInteger(NBT_KEY);
            StorageType[] values = values();
            if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        }

        return ITEM;
    }

    /**
     * Write this storage type to NBT.
     * Also writes legacy boolean keys for backward compatibility with older clients.
     *
     * @param nbt The NBT compound to write to
     */
    public void writeToNBT(net.minecraft.nbt.NBTTagCompound nbt) {
        nbt.setInteger(NBT_KEY, ordinal());
    }

    public boolean isItem() {
        return this == ITEM;
    }

    public boolean isFluid() {
        return this == FLUID;
    }

    public boolean isEssentia() {
        return this == ESSENTIA;
    }

    public boolean isGas() {
        return this == GAS;
    }
}
