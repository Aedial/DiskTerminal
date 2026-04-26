package com.cellterminal.network.chunked;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Client-side helper that applies {@link PayloadMode#FULL} or {@link PayloadMode#DELTA} payloads
 * to a state map keyed by entry ID. Mirrors the producer-side
 * {@link com.cellterminal.container.handler.DeltaSnapshot}.
 * <p>
 * For FULL payloads the target map is cleared and repopulated from the {@code listKey} list.
 * For DELTA payloads the {@code added}, {@code updated} and {@code removed} lists are applied
 * incrementally, leaving unaffected entries untouched.
 * <p>
 * The {@code parser} converts an entry NBT compound into the consumer's domain object. The
 * {@code idExtractor} reads the ID from a parsed object (for ordering / equality with the NBT
 * {@code idKey}).
 */
@SideOnly(Side.CLIENT)
public final class DeltaApplier {

    private DeltaApplier() {}

    /**
     * Apply a FULL or DELTA payload to a state map.
     *
     * @param mode payload mode (FULL replaces, DELTA merges)
     * @param payload the decoded NBT payload
     * @param target the map to mutate
     * @param parser converts an entry compound into the consumer's domain object
     * @param idFromObject extracts the long ID from a parsed object (used for FULL parsing)
     * @param <T> consumer domain type
     */
    public static <T> void apply(PayloadMode mode, NBTTagCompound payload, Map<Long, T> target,
                                 Function<NBTTagCompound, T> parser, Function<T, Long> idFromObject) {
        if (mode == PayloadMode.FULL) {
            applyFull(payload, target, parser, idFromObject);
        } else {
            applyDelta(payload, target, parser);
        }
    }

    private static <T> void applyFull(NBTTagCompound payload, Map<Long, T> target,
                                       Function<NBTTagCompound, T> parser,
                                       Function<T, Long> idFromObject) {
        target.clear();

        // The list key is whichever NBTTagList of compounds is present at the top level. The
        // producer always uses a single such list per channel, so we discover it dynamically
        // rather than requiring the caller to repeat the listKey. (Tiny overhead, simpler API.)
        String listKey = findListKey(payload);
        if (listKey == null) return;

        NBTTagList list = payload.getTagList(listKey, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            T parsed = parser.apply(list.getCompoundTagAt(i));
            if (parsed != null) target.put(idFromObject.apply(parsed), parsed);
        }
    }

    private static <T> void applyDelta(NBTTagCompound payload, Map<Long, T> target,
                                        Function<NBTTagCompound, T> parser) {
        // The DELTA payload format is documented in DeltaSnapshot#buildDelta.
        String idKey = payload.getString("idKey");
        if (idKey.isEmpty()) idKey = "id";

        NBTTagList added = payload.getTagList("added", Constants.NBT.TAG_COMPOUND);
        NBTTagList updated = payload.getTagList("updated", Constants.NBT.TAG_COMPOUND);
        NBTTagList removed = payload.getTagList("removed", Constants.NBT.TAG_COMPOUND);

        // Removals first so an updated entry that happened to take a removed slot doesn't get
        // wiped accidentally.
        for (int i = 0; i < removed.tagCount(); i++) {
            target.remove(removed.getCompoundTagAt(i).getLong(idKey));
        }

        for (int i = 0; i < added.tagCount(); i++) {
            NBTTagCompound entry = added.getCompoundTagAt(i);
            T parsed = parser.apply(entry);
            if (parsed != null) target.put(entry.getLong(idKey), parsed);
        }

        for (int i = 0; i < updated.tagCount(); i++) {
            NBTTagCompound entry = updated.getCompoundTagAt(i);
            T parsed = parser.apply(entry);
            if (parsed != null) target.put(entry.getLong(idKey), parsed);
        }
    }

    /**
     * Find the first NBTTagList of compounds in the payload. Used by FULL-mode parsing to
     * discover the data list without hardcoding its key.
     */
    private static String findListKey(NBTTagCompound payload) {
        for (String key : payload.getKeySet()) {
            if (payload.getTag(key) instanceof NBTTagList) {
                NBTTagList l = (NBTTagList) payload.getTag(key);
                if (l.getTagType() == Constants.NBT.TAG_COMPOUND || l.tagCount() == 0) return key;
            }
        }
        return null;
    }

    /**
     * Variant for cases where the consumer wants to apply directly without keeping a parsed map
     * (e.g. raw NBT consumers). The {@code applier} is invoked with (id, addedOrUpdatedNbt) for
     * upserts, with a null compound for removals. {@code clearForFull} is called before applying
     * a FULL payload.
     *
     * @param mode payload mode
     * @param payload payload data
     * @param idKey id field name in entries
     * @param listKey list key (used for FULL mode discovery; can be null to auto-detect)
     * @param clearForFull invoked when the payload is FULL, to clear consumer state
     * @param applier (id, entryOrNull) callback
     */
    public static void applyRaw(PayloadMode mode, NBTTagCompound payload, String idKey, String listKey,
                                Runnable clearForFull, BiFunction<Long, NBTTagCompound, Void> applier) {
        if (mode == PayloadMode.FULL) {
            clearForFull.run();
            String key = (listKey != null) ? listKey : findListKey(payload);
            if (key == null) return;
            NBTTagList list = payload.getTagList(key, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                applier.apply(entry.getLong(idKey), entry);
            }
            return;
        }

        String resolvedIdKey = payload.getString("idKey");
        if (resolvedIdKey.isEmpty()) resolvedIdKey = idKey;

        NBTTagList added = payload.getTagList("added", Constants.NBT.TAG_COMPOUND);
        NBTTagList updated = payload.getTagList("updated", Constants.NBT.TAG_COMPOUND);
        NBTTagList removed = payload.getTagList("removed", Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < removed.tagCount(); i++) {
            applier.apply(removed.getCompoundTagAt(i).getLong(resolvedIdKey), null);
        }
        for (int i = 0; i < added.tagCount(); i++) {
            NBTTagCompound e = added.getCompoundTagAt(i);
            applier.apply(e.getLong(resolvedIdKey), e);
        }
        for (int i = 0; i < updated.tagCount(); i++) {
            NBTTagCompound e = updated.getCompoundTagAt(i);
            applier.apply(e.getLong(resolvedIdKey), e);
        }
    }
}
