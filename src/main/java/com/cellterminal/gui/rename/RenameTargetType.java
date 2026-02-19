package com.cellterminal.gui.rename;


/**
 * Types of rename targets for the server-side rename packet.
 */
public enum RenameTargetType {

    /**
     * Rename a storage device (ME Drive / ME Chest).
     * Uses ICustomNameObject on the TileEntity.
     */
    STORAGE,

    /**
     * Rename a cell (ItemStack display name, like an anvil rename).
     * Requires storage ID + cell slot index.
     */
    CELL,

    /**
     * Rename a storage bus (IPart custom name).
     * Uses ICustomNameObject on the part.
     */
    STORAGE_BUS,

    /**
     * Rename a subnet (interface custom name).
     * This is handled by the existing PacketSubnetAction, kept here for completeness.
     */
    SUBNET
}
