package com.cellterminal.util;

import java.math.BigInteger;


/**
 * Utility methods for safe arithmetic operations that handle overflow gracefully.
 * Designed for handling modded AE2 cells with potentially max-long item counts.
 *
 * IMPORTANT: This class does NOT silently discard data. Methods that could overflow
 * will throw ArithmeticException rather than lose items.
 */
public final class SafeMath {

    /** Maximum value that fits in a long, as BigInteger for comparison */
    public static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    /** Minimum value that fits in a long, as BigInteger for comparison */
    public static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);

    private SafeMath() {}

    /**
     * Add two long values, throwing if overflow would occur.
     * Use this when data loss is unacceptable.
     *
     * @throws ArithmeticException if the result would overflow
     */
    public static long addExact(final long a, final long b) {
        return Math.addExact(a, b);
    }

    /**
     * Subtract two long values, throwing if overflow would occur.
     * Use this when data loss is unacceptable.
     *
     * @throws ArithmeticException if the result would overflow
     */
    public static long subtractExact(final long a, final long b) {
        return Math.subtractExact(a, b);
    }

    /**
     * Check if adding two long values would overflow.
     * @return true if a + b would overflow a long
     */
    public static boolean wouldOverflow(final long a, final long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return true;
        if (b < 0 && a < Long.MIN_VALUE - b) return true;

        return false;
    }

    /**
     * Add two values as BigInteger, allowing arbitrarily large results.
     * Use this when collecting totals that may exceed Long.MAX_VALUE.
     */
    public static BigInteger addBig(final long a, final long b) {
        return BigInteger.valueOf(a).add(BigInteger.valueOf(b));
    }

    /**
     * Add a long to a BigInteger.
     */
    public static BigInteger addBig(final BigInteger a, final long b) {
        return a.add(BigInteger.valueOf(b));
    }

    /**
     * Subtract a long from a BigInteger.
     */
    public static BigInteger subtractBig(final BigInteger a, final long b) {
        return a.subtract(BigInteger.valueOf(b));
    }

    /**
     * Check if a BigInteger fits in a long.
     */
    public static boolean fitsInLong(final BigInteger value) {
        return value.compareTo(MAX_LONG) <= 0 && value.compareTo(MIN_LONG) >= 0;
    }

    /**
     * Convert BigInteger to long, throwing if it doesn't fit.
     * @throws ArithmeticException if value exceeds Long.MAX_VALUE or is below Long.MIN_VALUE
     */
    public static long toLongExact(final BigInteger value) {
        if (!fitsInLong(value)) {
            throw new ArithmeticException("BigInteger value " + value + " exceeds long range");
        }

        return value.longValue();
    }

    /**
     * Extract up to Long.MAX_VALUE from a BigInteger, returning the amount extracted.
     * Modifies the passed holder array to store the remainder.
     *
     * @param total the total amount (as BigInteger)
     * @param remainder single-element array to receive the remainder after extraction
     * @return the amount extracted (fits in a long)
     */
    public static long extractUpToMax(final BigInteger total, final BigInteger[] remainder) {
        if (total.compareTo(MAX_LONG) <= 0) {
            remainder[0] = BigInteger.ZERO;
            return total.longValue();
        }

        remainder[0] = total.subtract(MAX_LONG);
        return Long.MAX_VALUE;
    }
}
