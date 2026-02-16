package com.cellterminal.util;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import appeng.api.storage.data.IAEStack;


/**
 * Tracks IAEStack counts using BigInteger to support totals exceeding Long.MAX_VALUE.
 * This is essential when combining items from multiple cells that each contain
 * near-maximum item counts.
 *
 * The tracker stores:
 * - A template IAEStack for each unique key (with arbitrary count)
 * - The true total count as BigInteger
 *
 * This ensures NO DATA LOSS when aggregating across multiple cells.
 */
public class BigStackTracker {

    /**
     * Entry holding both the stack template and its true count.
     */
    public static class Entry {
        private final IAEStack<?> template;
        private BigInteger count;

        Entry(IAEStack<?> template, long initialCount) {
            this.template = template.copy();
            this.template.setStackSize(1); // Template holds type info only
            this.count = BigInteger.valueOf(initialCount);
        }

        /**
         * Get the stack template (type information, count is always 1).
         */
        public IAEStack<?> getTemplate() {
            return this.template;
        }

        /**
         * Get the true total count as BigInteger.
         */
        public BigInteger getCount() {
            return this.count;
        }

        /**
         * Check if the count fits in a single long (single cell).
         */
        public boolean fitsInLong() {
            return SafeMath.fitsInLong(this.count);
        }

        /**
         * Get count as long, throwing if it doesn't fit.
         * @throws ArithmeticException if count exceeds Long.MAX_VALUE
         */
        public long getCountAsLong() {
            return SafeMath.toLongExact(this.count);
        }

        /**
         * Create an IAEStack with the full count (throws if count exceeds Long.MAX_VALUE).
         * @throws ArithmeticException if count exceeds Long.MAX_VALUE
         */
        @SuppressWarnings("unchecked")
        public <T extends IAEStack<T>> T createStack() {
            T stack = (T) this.template.copy();
            stack.setStackSize(getCountAsLong());

            return stack;
        }

        void addCount(long amount) {
            this.count = this.count.add(BigInteger.valueOf(amount));
        }

        void subtractCount(long amount) {
            this.count = this.count.subtract(BigInteger.valueOf(amount));
        }

        void subtractCount(BigInteger amount) {
            this.count = this.count.subtract(amount);
        }
    }

    private final Map<Object, Entry> entries = new LinkedHashMap<>();

    /**
     * Add a stack's count to the tracker.
     * @param key The unique key for this stack type
     * @param stack The stack (type and count)
     */
    public void add(Object key, IAEStack<?> stack) {
        Entry entry = this.entries.get(key);
        if (entry != null) {
            entry.addCount(stack.getStackSize());
        } else {
            this.entries.put(key, new Entry(stack, stack.getStackSize()));
        }
    }

    /**
     * Get an entry by key.
     */
    public Entry get(Object key) {
        return this.entries.get(key);
    }

    /**
     * Check if a key exists in the tracker.
     */
    public boolean containsKey(Object key) {
        return this.entries.containsKey(key);
    }

    /**
     * Get all entries.
     */
    public Set<Map.Entry<Object, Entry>> entrySet() {
        return this.entries.entrySet();
    }

    /**
     * Get all keys.
     */
    public Set<Object> keySet() {
        return this.entries.keySet();
    }

    /**
     * Get the number of unique stack types being tracked.
     */
    public int size() {
        return this.entries.size();
    }

    /**
     * Check if the tracker is empty.
     */
    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /**
     * Remove an entry by key.
     */
    public Entry remove(Object key) {
        return this.entries.remove(key);
    }

    /**
     * Subtract an amount from an entry's count.
     */
    public void subtractCount(Object key, long amount) {
        Entry entry = this.entries.get(key);
        if (entry != null) entry.subtractCount(amount);
    }

    /**
     * Subtract a BigInteger amount from an entry's count.
     */
    public void subtractCount(Object key, BigInteger amount) {
        Entry entry = this.entries.get(key);
        if (entry != null) entry.subtractCount(amount);
    }

    /**
     * Calculate the total capacity needed to store all tracked items.
     * Returns a BigInteger to handle totals exceeding Long.MAX_VALUE.
     */
    public BigInteger getTotalCount() {
        BigInteger total = BigInteger.ZERO;
        for (Entry entry : this.entries.values()) total = total.add(entry.count);

        return total;
    }

    /**
     * Check if any single stack type exceeds Long.MAX_VALUE.
     * If true, we'd need multiple cells for that single item type.
     */
    public boolean hasOversizedEntry() {
        for (Entry entry : this.entries.values()) {
            if (!entry.fitsInLong()) return true;
        }

        return false;
    }
}
