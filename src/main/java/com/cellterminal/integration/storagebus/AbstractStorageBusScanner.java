package com.cellterminal.integration.storagebus;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Base class for storage bus scanners.
 * TODO: Add connected tile retrieval helper methods here (getTile, getSide, etc).
 */
public abstract class AbstractStorageBusScanner implements IStorageBusScanner {

    /**
     * Apply common capability flags to the provided NBT payload.
     */
    protected void applyCapabilities(NBTTagCompound nbt) {
        applyCapabilities(nbt, supportsPriority(), supportsIOMode());
    }

    /**
     * Apply capability flags to the provided NBT payload.
     */
    protected void applyCapabilities(NBTTagCompound nbt, boolean supportsPriority, boolean supportsIOMode) {
        nbt.setBoolean("supportsPriority", supportsPriority);
        nbt.setBoolean("supportsIOMode", supportsIOMode);
    }

    /**
     * Default base number of config slots without capacity upgrades.
     */
    protected int getBaseConfigSlots() {
        return 18;
    }

    /**
     * Default extra slots per capacity upgrade.
     */
    protected int getSlotsPerCapacityUpgrade() {
        return 9;
    }

    /**
     * Default maximum config slots (with 5 upgrades).
     */
    protected int getMaxConfigSlots() {
        return 63;
    }

    /**
     * Apply slot limit parameters to the provided NBT payload.
     */
    protected void applySlotParameters(NBTTagCompound nbt) {
        applySlotParameters(nbt, getBaseConfigSlots(), getSlotsPerCapacityUpgrade(), getMaxConfigSlots());
    }

    /**
     * Apply slot limit parameters to the provided NBT payload.
     */
    protected void applySlotParameters(NBTTagCompound nbt, int baseConfigSlots, int slotsPerUpgrade, int maxConfigSlots) {
        nbt.setInteger("baseConfigSlots", baseConfigSlots);
        nbt.setInteger("slotsPerUpgrade", slotsPerUpgrade);
        nbt.setInteger("maxConfigSlots", maxConfigSlots);
    }
}
