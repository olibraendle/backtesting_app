package com.backtester.core.portfolio;

/**
 * Trading side - long or short.
 */
public enum Side {
    LONG(1),
    SHORT(-1);

    private final int multiplier;

    Side(int multiplier) {
        this.multiplier = multiplier;
    }

    /**
     * Returns 1 for LONG, -1 for SHORT.
     * Useful for P&L calculations.
     */
    public int getMultiplier() {
        return multiplier;
    }

    /**
     * Returns the opposite side.
     */
    public Side opposite() {
        return this == LONG ? SHORT : LONG;
    }
}
