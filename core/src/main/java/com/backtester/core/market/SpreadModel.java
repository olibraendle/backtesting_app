package com.backtester.core.market;

import com.backtester.common.model.Bar;

/**
 * Spread simulation model.
 * Spread represents the bid-ask difference that affects execution price.
 */
public class SpreadModel {
    private final SpreadType type;
    private final double value;

    public enum SpreadType {
        NONE,            // No spread (ideal conditions)
        FIXED_PIPS,      // Fixed spread in pips (0.0001 for forex)
        FIXED_POINTS,    // Fixed spread in price points
        PERCENTAGE,      // Percentage of price
        DYNAMIC          // Dynamic based on volatility (ATR-based)
    }

    public SpreadModel(SpreadType type, double value) {
        this.type = type;
        this.value = value;
    }

    /**
     * Calculate spread for a given price.
     *
     * @param price Current price
     * @param bar   Current bar (for dynamic spread calculation)
     * @return Half spread (to add/subtract from mid price)
     */
    public double calculateHalfSpread(double price, Bar bar) {
        return switch (type) {
            case NONE -> 0;
            case FIXED_PIPS -> value * 0.0001;
            case FIXED_POINTS -> value;
            case PERCENTAGE -> price * (value / 100.0);
            case DYNAMIC -> calculateDynamicSpread(price, bar);
        };
    }

    /**
     * Calculate full spread.
     */
    public double calculateFullSpread(double price, Bar bar) {
        return calculateHalfSpread(price, bar) * 2;
    }

    /**
     * Get bid price (price at which you can sell).
     */
    public double getBidPrice(double midPrice, Bar bar) {
        return midPrice - calculateHalfSpread(midPrice, bar);
    }

    /**
     * Get ask price (price at which you can buy).
     */
    public double getAskPrice(double midPrice, Bar bar) {
        return midPrice + calculateHalfSpread(midPrice, bar);
    }

    /**
     * Dynamic spread based on bar volatility.
     */
    private double calculateDynamicSpread(double price, Bar bar) {
        // Base spread as percentage + volatility component
        double baseSpread = price * (value / 100.0);
        double volatilityComponent = bar.range() * 0.1; // 10% of bar range
        return baseSpread + volatilityComponent;
    }

    // ===== Factory Methods =====

    /**
     * No spread.
     */
    public static SpreadModel zero() {
        return new SpreadModel(SpreadType.NONE, 0);
    }

    /**
     * Fixed spread in pips (forex).
     */
    public static SpreadModel pips(double pips) {
        return new SpreadModel(SpreadType.FIXED_PIPS, pips);
    }

    /**
     * Fixed spread in price points.
     */
    public static SpreadModel points(double points) {
        return new SpreadModel(SpreadType.FIXED_POINTS, points);
    }

    /**
     * Percentage of price.
     */
    public static SpreadModel percentage(double percent) {
        return new SpreadModel(SpreadType.PERCENTAGE, percent);
    }

    /**
     * Dynamic spread based on volatility.
     */
    public static SpreadModel dynamic(double basePercent) {
        return new SpreadModel(SpreadType.DYNAMIC, basePercent);
    }

    // ===== Getters =====

    public SpreadType getType() {
        return type;
    }

    public double getValue() {
        return value;
    }

    @Override
    public String toString() {
        return switch (type) {
            case NONE -> "No spread";
            case FIXED_PIPS -> String.format("%.1f pips", value);
            case FIXED_POINTS -> String.format("%.5f points", value);
            case PERCENTAGE -> String.format("%.3f%%", value);
            case DYNAMIC -> String.format("Dynamic (base %.3f%%)", value);
        };
    }
}
