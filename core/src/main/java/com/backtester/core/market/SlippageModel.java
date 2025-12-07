package com.backtester.core.market;

import com.backtester.common.model.Bar;

import java.util.Random;

/**
 * Slippage simulation model.
 * Slippage represents the difference between expected and actual execution price.
 *
 * IMPORTANT: For volume-based slippage, uses PREVIOUS bar's volume to avoid
 * lookahead bias. Current bar's volume is only known at bar close.
 */
public class SlippageModel {
    private final SlippageType type;
    private final double value;
    private final Random random;
    private Bar previousBar; // Track previous bar for volume-based slippage

    public enum SlippageType {
        NONE,           // No slippage (ideal conditions)
        FIXED_PERCENT,  // Fixed percentage of price
        FIXED_POINTS,   // Fixed price points
        RANDOM_PERCENT, // Random 0 to value percent
        VOLUME_BASED    // Based on volume (uses PREVIOUS bar's volume)
    }

    public SlippageModel(SlippageType type, double value) {
        this.type = type;
        this.value = value;
        this.random = new Random();
        this.previousBar = null;
    }

    /**
     * Update the previous bar reference - call this at end of each bar.
     * Required for volume-based slippage calculation to avoid lookahead bias.
     */
    public void updatePreviousBar(Bar bar) {
        this.previousBar = bar;
    }

    /**
     * Calculate slippage for a trade.
     *
     * @param price         Current price
     * @param quantity      Trade quantity
     * @param bar           Current bar (used for reference, but volume-based uses previousBar)
     * @param isBuy         True if buying, false if selling
     * @return Slippage amount (always positive, direction handled by caller)
     */
    public double calculate(double price, double quantity, Bar bar, boolean isBuy) {
        return switch (type) {
            case NONE -> 0;
            case FIXED_PERCENT -> price * (value / 100.0);
            case FIXED_POINTS -> value;
            case RANDOM_PERCENT -> price * (random.nextDouble() * value / 100.0);
            case VOLUME_BASED -> calculateVolumeBased(price, quantity);
        };
    }

    /**
     * Calculate slippage as percentage of price.
     */
    public double calculatePercent(double price, double quantity, Bar bar, boolean isBuy) {
        double slippage = calculate(price, quantity, bar, isBuy);
        return (slippage / price) * 100;
    }

    /**
     * Volume-based slippage: larger orders relative to volume have more slippage.
     * Uses PREVIOUS bar's volume to avoid lookahead bias - current bar's volume
     * is only known at bar close.
     */
    private double calculateVolumeBased(double price, double quantity) {
        // Base slippage always applies
        double baseSlippage = price * (value / 100.0) * 0.5;

        // Use PREVIOUS bar's volume for volume impact (avoids lookahead)
        // If no previous bar available, use only base slippage
        if (previousBar != null && previousBar.volume() > 0) {
            // Order size as fraction of previous bar's volume
            double volumeRatio = (quantity * price) / (previousBar.volume() * previousBar.typicalPrice());

            // Volume impact: 1bp per 1% of volume
            double volumeImpact = price * volumeRatio * 0.01;

            return baseSlippage + volumeImpact;
        }

        return baseSlippage;
    }

    // ===== Factory Methods =====

    /**
     * No slippage.
     */
    public static SlippageModel zero() {
        return new SlippageModel(SlippageType.NONE, 0);
    }

    /**
     * Fixed percentage slippage.
     */
    public static SlippageModel fixedPercent(double percent) {
        return new SlippageModel(SlippageType.FIXED_PERCENT, percent);
    }

    /**
     * Fixed points slippage.
     */
    public static SlippageModel fixedPoints(double points) {
        return new SlippageModel(SlippageType.FIXED_POINTS, points);
    }

    /**
     * Random slippage up to max percent.
     */
    public static SlippageModel randomPercent(double maxPercent) {
        return new SlippageModel(SlippageType.RANDOM_PERCENT, maxPercent);
    }

    /**
     * Volume-based slippage.
     */
    public static SlippageModel volumeBased(double basePercent) {
        return new SlippageModel(SlippageType.VOLUME_BASED, basePercent);
    }

    // ===== Getters =====

    public SlippageType getType() {
        return type;
    }

    public double getValue() {
        return value;
    }

    @Override
    public String toString() {
        return switch (type) {
            case NONE -> "No slippage";
            case FIXED_PERCENT -> String.format("%.3f%%", value);
            case FIXED_POINTS -> String.format("%.5f points", value);
            case RANDOM_PERCENT -> String.format("0-%.3f%% random", value);
            case VOLUME_BASED -> String.format("Volume-based (base %.3f%%)", value);
        };
    }
}
