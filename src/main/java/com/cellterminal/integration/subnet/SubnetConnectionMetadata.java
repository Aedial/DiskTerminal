package com.cellterminal.integration.subnet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import appeng.api.parts.IPart;
import appeng.api.parts.PartItemStack;
import appeng.helpers.ICustomNameObject;


/**
 * Shared access to names and favorites attached to subnet connection tiles and parts.
 */
public final class SubnetConnectionMetadata {

    private SubnetConnectionMetadata() {}

    public static void writeToNBT(Object host, TileEntity tile, EnumFacing side, NBTTagCompound nbt) {
        // Read the connection's custom name through AE2's ICustomNameObject when available
        String customName = tile.getTileData().getString(getKey("cellterminal.name", host, tile, side));
        if (host instanceof ICustomNameObject) {
            ICustomNameObject nameable = (ICustomNameObject) host;
            if (nameable.hasCustomInventoryName()) customName = nameable.getCustomInventoryName();
        }

        if (customName != null && !customName.isEmpty()) nbt.setString("customName", customName);

        // Read favorite state from the host tile's data (Forge's getTileData)
        // Side-specific keys distinguish parts attached to the same cable
        if (tile.getTileData().getBoolean(getKey("cellterminal.favorite", host, tile, side))) {
            nbt.setBoolean("favorite", true);
        }
    }

    public static void rename(Object host, TileEntity tile, EnumFacing side, String newName) {
        // TileInterface and PartInterface expose names through ICustomNameObject, as do other AE2 parts
        // Use AE2's built-in custom name mechanism when the connection supports it
        // Empty string clears the name, non-empty sets it
        String name = newName.trim();
        String nameKey = getKey("cellterminal.name", host, tile, side);
        NBTTagCompound tileData = tile.getTileData();
        if (host instanceof ICustomNameObject) {
            if (name.isEmpty() && host instanceof IPart) {
                ((IPart) host).getItemStack(PartItemStack.WORLD).clearCustomName();
            } else {
                ((ICustomNameObject) host).setCustomName(name.isEmpty() ? null : name);
            }

            tileData.removeTag(nameKey);
        } else if (name.isEmpty()) {
            tileData.removeTag(nameKey);
        } else {
            tileData.setString(nameKey, name);
        }

        // Mark the host tile dirty so it saves
        tile.markDirty();
    }

    public static void setFavorite(Object host, TileEntity tile, EnumFacing side, boolean favorite) {
        // Use Forge's getTileData() for persistent custom data in the tile's NBT
        // The host tile holds this data for both full blocks and cable-attached parts
        // World saves persist the data, with a separate key for each attached part
        String favoriteKey = getKey("cellterminal.favorite", host, tile, side);
        NBTTagCompound tileData = tile.getTileData();
        if (favorite) {
            tileData.setBoolean(favoriteKey, true);
        } else {
            // Remove the key when the connection has no favorite flag to keep NBT clean
            tileData.removeTag(favoriteKey);
        }

        // Mark the tile dirty so it saves
        tile.markDirty();
    }

    /**
     * Get the metadata key for the connection host.
     * Full blocks use the supplied key, including the existing "cellterminal.favorite" key.
     * Parts include the side to distinguish attachments on the same cable.
     */
    private static String getKey(String key, Object host, TileEntity tile, EnumFacing side) {
        return host == tile ? key : key + "." + side.ordinal();
    }
}
