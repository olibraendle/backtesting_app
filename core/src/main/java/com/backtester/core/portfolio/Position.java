package com.backtester.core.portfolio;

/**
 * Represents an open position in the portfolio.
 */
public class Position {
    private final String symbol;
    private final Side side;
    private final double entryPrice;
    private final double quantity;
    private final long entryTime;
    private final int entryBarIndex;

    private double currentPrice;
    private double unrealizedPnl;
    private double unrealizedPnlPercent;
    private double maxPrice;
    private double minPrice;
    private double maxUnrealizedPnl;
    private double maxUnrealizedLoss;

    public Position(String symbol, Side side, double entryPrice, double quantity,
                    long entryTime, int entryBarIndex) {
        this.symbol = symbol;
        this.side = side;
        this.entryPrice = entryPrice;
        this.quantity = quantity;
        this.entryTime = entryTime;
        this.entryBarIndex = entryBarIndex;
        this.currentPrice = entryPrice;
        this.maxPrice = entryPrice;
        this.minPrice = entryPrice;
        this.unrealizedPnl = 0;
        this.maxUnrealizedPnl = 0;
        this.maxUnrealizedLoss = 0;
    }

    /**
     * Update position with current market price.
     */
    public void updatePrice(double price) {
        this.currentPrice = price;
        this.maxPrice = Math.max(maxPrice, price);
        this.minPrice = Math.min(minPrice, price);

        // Calculate unrealized P&L
        double priceDiff = price - entryPrice;
        this.unrealizedPnl = priceDiff * quantity * side.getMultiplier();
        this.unrealizedPnlPercent = (priceDiff / entryPrice) * 100 * side.getMultiplier();

        // Track max profit and loss
        this.maxUnrealizedPnl = Math.max(maxUnrealizedPnl, unrealizedPnl);
        this.maxUnrealizedLoss = Math.min(maxUnrealizedLoss, unrealizedPnl);
    }

    /**
     * Get position value at current price.
     */
    public double getValue() {
        return currentPrice * quantity;
    }

    /**
     * Get position value at entry.
     */
    public double getEntryValue() {
        return entryPrice * quantity;
    }

    /**
     * Calculate the maximum adverse excursion (MAE) - worst unrealized loss.
     */
    public double getMaxAdverseExcursion() {
        if (side == Side.LONG) {
            return (minPrice - entryPrice) * quantity;
        } else {
            return (entryPrice - maxPrice) * quantity;
        }
    }

    /**
     * Calculate the maximum favorable excursion (MFE) - best unrealized profit.
     */
    public double getMaxFavorableExcursion() {
        if (side == Side.LONG) {
            return (maxPrice - entryPrice) * quantity;
        } else {
            return (entryPrice - minPrice) * quantity;
        }
    }

    // Getters
    public String getSymbol() {
        return symbol;
    }

    public Side getSide() {
        return side;
    }

    public double getEntryPrice() {
        return entryPrice;
    }

    public double getQuantity() {
        return quantity;
    }

    public long getEntryTime() {
        return entryTime;
    }

    public int getEntryBarIndex() {
        return entryBarIndex;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public double getUnrealizedPnl() {
        return unrealizedPnl;
    }

    public double getUnrealizedPnlPercent() {
        return unrealizedPnlPercent;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public double getMinPrice() {
        return minPrice;
    }

    public boolean isLong() {
        return side == Side.LONG;
    }

    public boolean isShort() {
        return side == Side.SHORT;
    }

    @Override
    public String toString() {
        return String.format("Position[%s %s %.4f @ %.5f, PnL=%.2f (%.2f%%)]",
                side, symbol, quantity, entryPrice, unrealizedPnl, unrealizedPnlPercent);
    }
}
