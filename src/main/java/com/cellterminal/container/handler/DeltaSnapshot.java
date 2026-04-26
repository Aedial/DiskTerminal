package com.cellterminal.container.handler;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;


/**
 * Per-channel server-side snapshot tracker for delta updates.
 * <p>
 * Holds the last full payload we sent for a given channel, keyed by entry ID. On the next regen,
 * the new entries are diffed against the snapshot and an incremental payload is produced
 * containing:
 * <ul>
 *   <li>{@code added}: NBTTagList of compounds present in the new state but not in the old</li>
 *   <li>{@code updated}: NBTTagList of compounds present in both with changed serialization</li>
 *   <li>{@code removed}: NBTTagList of long IDs present in old but not new</li>
 * </ul>
 * Static (non-list) keys (e.g. {@code networkId}, {@code terminalPos}) are always included in
 * every payload so the client always has them in sync.
 * <p>
 * Reset whenever the underlying network/grid identity changes (e.g. switching subnets) so the
 * next payload is forced to be a full rebuild on the client.
 */
public class DeltaSnapshot {

    /**
     * Wrapper for the result of {@link #buildDelta(NBTTagCompound, String, String)}.
     */
    public static class DeltaResult {
        public final NBTTagCompound payload;
        public final boolean isFull;

        public DeltaResult(NBTTagCompound payload, boolean isFull) {
            this.payload = payload;
            this.isFull = isFull;
        }
    }

    /**
     * Map: channel -> (entryId -> last-sent NBT compound for that entry).
     * Empty map means we have never sent a full payload yet (next send must be FULL).
     */
    private final Map<String, Map<Long, NBTTagCompound>> snapshots = new HashMap<>();

    /**
     * Reset the snapshot for one channel (next send on that channel will be FULL).
     */
    public void reset(String channel) {
        snapshots.remove(channel);
    }

    /**
     * Reset all snapshots. Use when network/grid identity changes underneath us.
     */
    public void resetAll() {
        snapshots.clear();
    }

    /**
     * Compute a delta payload for one channel.
     *
     * @param channel the channel being sent on (used as the snapshot key)
     * @param fullPayload the freshly-built full NBT (must contain a list of compounds at {@code listKey})
     * @param listKey the NBT key of the entry list inside {@code fullPayload}
     * @param idKey the field name inside each entry that uniquely identifies it (typically {@code "id"})
     */
    public DeltaResult buildDelta(String channel, NBTTagCompound fullPayload, String listKey, String idKey) {
        NBTTagList list = fullPayload.getTagList(listKey, Constants.NBT.TAG_COMPOUND);
        Map<Long, NBTTagCompound> oldSnapshot = snapshots.get(channel);

        // Build new snapshot map first (we always commit it after sending, regardless of mode).
        Map<Long, NBTTagCompound> newSnapshot = new HashMap<>(list.tagCount() * 2);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.hasKey(idKey)) continue;
            newSnapshot.put(entry.getLong(idKey), entry);
        }

        // First send on this channel: full snapshot.
        if (oldSnapshot == null) {
            snapshots.put(channel, newSnapshot);
            return new DeltaResult(fullPayload, true);
        }

        // Compute diff
        NBTTagList added = new NBTTagList();
        NBTTagList updated = new NBTTagList();
        NBTTagList removedIds = new NBTTagList();

        for (Map.Entry<Long, NBTTagCompound> e : newSnapshot.entrySet()) {
            NBTTagCompound oldEntry = oldSnapshot.get(e.getKey());
            if (oldEntry == null) {
                added.appendTag(e.getValue());
            } else if (!nbtEquals(oldEntry, e.getValue())) {
                updated.appendTag(e.getValue());
            }
        }

        for (Long oldId : oldSnapshot.keySet()) {
            if (!newSnapshot.containsKey(oldId)) {
                NBTTagCompound idTag = new NBTTagCompound();
                idTag.setLong(idKey, oldId);
                removedIds.appendTag(idTag);
            }
        }

        // Commit new snapshot.
        snapshots.put(channel, newSnapshot);

        // Build delta payload: copy all non-list static keys from full payload, then attach diff lists.
        NBTTagCompound delta = new NBTTagCompound();
        for (String key : fullPayload.getKeySet()) {
            if (key.equals(listKey)) continue;
            delta.setTag(key, fullPayload.getTag(key));
        }
        delta.setString("listKey", listKey);
        delta.setString("idKey", idKey);
        delta.setTag("added", added);
        delta.setTag("updated", updated);
        delta.setTag("removed", removedIds);

        return new DeltaResult(delta, false);
    }

    /**
     * Deep equality check between two NBT bases. Used to decide whether an entry counts as updated.
     */
    private static boolean nbtEquals(NBTBase a, NBTBase b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
