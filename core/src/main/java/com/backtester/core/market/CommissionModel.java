package com.backtester.core.market;

/**
 * Commission calculation model.
 */
public class CommissionModel {
    private final CommissionType type;
    private final double value;
    private final double minCommission;

    public enum CommissionType {
        FIXED,           // Fixed amount per trade
        PERCENTAGE,      // Percentage of trade value
        PER_SHARE        // Fixed amount per share/unit
    }

    public CommissionModel(CommissionType type, double value) {
        this(type, value, 0);
    }

    public CommissionModel(CommissionType type, double value, double minCommission) {
        this.type = type;
        this.value = value;
        this.minCommission = minCommission;
    }

    /**
     * Calculate commission for a trade.
     *
     * @param quantity Trade quantity
     * @param price    Trade price
     * @return Commission amount
     */
    public double calculate(double quantity, double price) {
        double commission = switch (type) {
            case FIXED -> value;
            case PERCENTAGE -> price * quantity * (value / 100.0);
            case PER_SHARE -> quantity * value;
        };
        return Math.max(commission, minCommission);
    }

    // ===== Factory Methods =====

    /**
     * No commission.
     */
    public static CommissionModel zero() {
        return new CommissionModel(CommissionType.FIXED, 0);
    }

    /**
     * Fixed commission per trade.
     */
    public static CommissionModel fixed(double amount) {
        return new CommissionModel(CommissionType.FIXED, amount);
    }

    /**
     * Percentage of trade value.
     */
    public static CommissionModel percentage(double percent) {
        return new CommissionModel(CommissionType.PERCENTAGE, percent);
    }

    /**
     * Per share/unit commission.
     */
    public static CommissionModel perShare(double amount) {
        return new CommissionModel(CommissionType.PER_SHARE, amount);
    }

    /**
     * Interactive Brokers style: per share with minimum.
     */
    public static CommissionModel interactiveBrokers() {
        return new CommissionModel(CommissionType.PER_SHARE, 0.005, 1.0);
    }

    // ===== Getters =====

    public CommissionType getType() {
        return type;
    }

    public double getValue() {
        return value;
    }

    public double getMinCommission() {
        return minCommission;
    }

    @Override
    public String toString() {
        return switch (type) {
            case FIXED -> String.format("$%.2f per trade", value);
            case PERCENTAGE -> String.format("%.3f%%", value);
            case PER_SHARE -> String.format("$%.4f per share (min $%.2f)", value, minCommission);
        };
    }
}
