package com.backtester.common.model;

/**
 * Represents different timeframes for market data bars.
 */
public enum TimeFrame {
    M1(60_000L, "1 Minute"),
    M5(300_000L, "5 Minutes"),
    M15(900_000L, "15 Minutes"),
    M30(1_800_000L, "30 Minutes"),
    H1(3_600_000L, "1 Hour"),
    H4(14_400_000L, "4 Hours"),
    D1(86_400_000L, "1 Day"),
    W1(604_800_000L, "1 Week"),
    UNKNOWN(0L, "Unknown");

    private final long milliseconds;
    private final String displayName;

    TimeFrame(long milliseconds, String displayName) {
        this.milliseconds = milliseconds;
        this.displayName = displayName;
    }

    public long getMilliseconds() {
        return milliseconds;
    }

    public long getSeconds() {
        return milliseconds / 1000;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Calculate the number of bars in a trading year (252 days)
     */
    public int barsPerYear() {
        return switch (this) {
            case M1 -> 252 * 24 * 60;      // ~362,880 bars
            case M5 -> 252 * 24 * 12;      // ~72,576 bars
            case M15 -> 252 * 24 * 4;      // ~24,192 bars
            case M30 -> 252 * 24 * 2;      // ~12,096 bars
            case H1 -> 252 * 24;           // ~6,048 bars
            case H4 -> 252 * 6;            // ~1,512 bars
            case D1 -> 252;                // 252 bars
            case W1 -> 52;                 // 52 bars
            case UNKNOWN -> 252;           // Default to daily
        };
    }

    /**
     * Detect timeframe from bar timestamps
     */
    public static TimeFrame detect(long timestampDiffMs) {
        for (TimeFrame tf : values()) {
            if (tf != UNKNOWN && Math.abs(timestampDiffMs - tf.milliseconds) < tf.milliseconds * 0.1) {
                return tf;
            }
        }
        return M5; // Default
    }

    /**
     * Get timeframe from milliseconds interval.
     */
    public static TimeFrame fromMilliseconds(long ms) {
        for (TimeFrame tf : values()) {
            if (tf != UNKNOWN && Math.abs(ms - tf.milliseconds) < tf.milliseconds * 0.2) {
                return tf;
            }
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
