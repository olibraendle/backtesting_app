package com.backtester.common.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Immutable OHLCV bar record representing a single price bar.
 * This is the fundamental data structure for all market data.
 *
 * @param timestamp Unix epoch milliseconds
 * @param open      Opening price
 * @param high      Highest price during the bar
 * @param low       Lowest price during the bar
 * @param close     Closing price
 * @param volume    Trading volume
 * @param index     Sequential bar index (0-based)
 */
public record Bar(
        long timestamp,
        double open,
        double high,
        double low,
        double close,
        double volume,
        int index
) implements Comparable<Bar> {

    /**
     * Typical price: (High + Low + Close) / 3
     */
    public double typicalPrice() {
        return (high + low + close) / 3.0;
    }

    /**
     * Bar range: High - Low
     */
    public double range() {
        return high - low;
    }

    /**
     * True range considering gaps from previous close
     */
    public double trueRange(double previousClose) {
        double highLow = high - low;
        double highPrevClose = Math.abs(high - previousClose);
        double lowPrevClose = Math.abs(low - previousClose);
        return Math.max(highLow, Math.max(highPrevClose, lowPrevClose));
    }

    /**
     * Bar body size: |Close - Open|
     */
    public double bodySize() {
        return Math.abs(close - open);
    }

    /**
     * Is this a bullish (green) bar?
     */
    public boolean isBullish() {
        return close > open;
    }

    /**
     * Is this a bearish (red) bar?
     */
    public boolean isBearish() {
        return close < open;
    }

    /**
     * Mid price: (High + Low) / 2
     */
    public double midPrice() {
        return (high + low) / 2.0;
    }

    /**
     * VWAP approximation using typical price
     */
    public double vwap() {
        return typicalPrice();
    }

    /**
     * Get timestamp as LocalDateTime (UTC)
     */
    public LocalDateTime getDateTime() {
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.of("UTC")
        );
    }

    /**
     * Get timestamp as LocalDateTime in specified timezone
     */
    public LocalDateTime getDateTime(ZoneId zone) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                zone
        );
    }

    @Override
    public int compareTo(Bar other) {
        return Long.compare(this.timestamp, other.timestamp);
    }

    /**
     * Create a new Bar with updated index
     */
    public Bar withIndex(int newIndex) {
        return new Bar(timestamp, open, high, low, close, volume, newIndex);
    }

    /**
     * Validate bar data integrity
     */
    public boolean isValid() {
        return high >= low &&
                high >= open &&
                high >= close &&
                low <= open &&
                low <= close &&
                open > 0 &&
                close > 0 &&
                volume >= 0;
    }

    @Override
    public String toString() {
        return String.format("Bar[%s O=%.5f H=%.5f L=%.5f C=%.5f V=%.0f]",
                getDateTime(), open, high, low, close, volume);
    }
}
