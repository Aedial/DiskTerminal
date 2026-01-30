package com.cellterminal.integration.storagebus;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Base class for storage bus scanners.
 * TODO: Add connected tile retrieval helper methods here (getTile, getSide, etc).
 */
public abstract class AbstractStorageBusScanner implements IStorageBusScanner {

    @Override
    public boolean supportsPriority() {
        return true;
    }

    @Override
    public boolean supportsIOMode() {
        return true;
    }

    /**
     * Apply common capability flags to the provided NBT payload.
     */
    protected void applyCapabilities(NBTTagCompound nbt) {
        nbt.setBoolean("supportsPriority", supportsPriority());
        nbt.setBoolean("supportsIOMode", supportsIOMode());
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
        nbt.setInteger("baseConfigSlots", getBaseConfigSlots());
        nbt.setInteger("slotsPerUpgrade", getSlotsPerCapacityUpgrade());
        nbt.setInteger("maxConfigSlots", getMaxConfigSlots());
    }
}
