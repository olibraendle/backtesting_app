package com.backtester.core.portfolio;

import com.backtester.common.model.Bar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Portfolio manages positions, cash, and tracks equity over time.
 */
public class Portfolio {
    private final double initialCash;
    private double cash;
    private Position currentPosition;
    private final List<Trade> completedTrades;
    private final List<EquityPoint> equityHistory;

    // Peak tracking for drawdown
    private double peakEquity;
    private double currentDrawdown;
    private double maxDrawdown;
    private double maxDrawdownPercent;

    // Statistics tracking
    private int totalBarsInMarket;
    private double maxPositionValue;

    public Portfolio(double initialCash) {
        this.initialCash = initialCash;
        this.cash = initialCash;
        this.currentPosition = null;
        this.completedTrades = new ArrayList<>();
        this.equityHistory = new ArrayList<>();
        this.peakEquity = initialCash;
        this.currentDrawdown = 0;
        this.maxDrawdown = 0;
        this.maxDrawdownPercent = 0;
    }

    /**
     * Get current equity (cash + position value).
     */
    public double getEquity() {
        double positionValue = currentPosition != null ? currentPosition.getValue() : 0;
        return cash + positionValue;
    }

    /**
     * Get available cash.
     */
    public double getCash() {
        return cash;
    }

    /**
     * Check if there's an open position.
     */
    public boolean hasPosition() {
        return currentPosition != null;
    }

    /**
     * Get current position (may be null).
     */
    public Position getPosition() {
        return currentPosition;
    }

    /**
     * Open a new position.
     */
    public void openPosition(String symbol, Side side, double price, double quantity,
                             long timestamp, int barIndex, double commission, double slippage) {
        if (currentPosition != null) {
            throw new IllegalStateException("Cannot open position - already have an open position");
        }

        // Apply slippage to entry price
        double adjustedPrice = side == Side.LONG
                ? price * (1 + slippage)
                : price * (1 - slippage);

        double cost = adjustedPrice * quantity + commission;

        if (cost > cash) {
            throw new IllegalStateException("Insufficient cash for position. Required: " + cost + ", Available: " + cash);
        }

        cash -= cost;
        currentPosition = new Position(symbol, side, adjustedPrice, quantity, timestamp, barIndex);

        // Track max position value
        maxPositionValue = Math.max(maxPositionValue, currentPosition.getEntryValue());
    }

    /**
     * Close the current position.
     */
    public Trade closePosition(double price, long timestamp, int barIndex,
                               double commission, double slippage) {
        if (currentPosition == null) {
            throw new IllegalStateException("No position to close");
        }

        Position pos = currentPosition;

        // Apply slippage to exit price
        double adjustedPrice = pos.getSide() == Side.LONG
                ? price * (1 - slippage)
                : price * (1 + slippage);

        // Calculate P&L
        double priceDiff = adjustedPrice - pos.getEntryPrice();
        double grossPnl = priceDiff * pos.getQuantity() * pos.getSide().getMultiplier();

        // Calculate total slippage cost
        double entrySlippage = Math.abs(pos.getEntryPrice() - price) * pos.getQuantity();
        double exitSlippage = Math.abs(adjustedPrice - price) * pos.getQuantity();
        double totalSlippage = entrySlippage + exitSlippage;

        // Total commission (entry + exit)
        double totalCommission = commission * 2;

        double netPnl = grossPnl - totalCommission;

        // Return proceeds to cash
        double proceeds = adjustedPrice * pos.getQuantity() - commission;
        cash += proceeds;

        // Create trade record
        Trade trade = Trade.builder()
                .symbol(pos.getSymbol())
                .side(pos.getSide())
                .entryTime(pos.getEntryTime())
                .exitTime(timestamp)
                .entryPrice(pos.getEntryPrice())
                .exitPrice(adjustedPrice)
                .quantity(pos.getQuantity())
                .grossPnl(grossPnl)
                .commission(totalCommission)
                .slippage(totalSlippage)
                .netPnl(netPnl)
                .barsHeld(barIndex - pos.getEntryBarIndex())
                .entryBarIndex(pos.getEntryBarIndex())
                .exitBarIndex(barIndex)
                .maxFavorableExcursion(pos.getMaxFavorableExcursion())
                .maxAdverseExcursion(pos.getMaxAdverseExcursion())
                .build();

        completedTrades.add(trade);
        currentPosition = null;

        return trade;
    }

    /**
     * Update portfolio with current bar prices.
     */
    public void update(Bar bar) {
        // Update position if exists
        if (currentPosition != null) {
            currentPosition.updatePrice(bar.close());
            totalBarsInMarket++;
        }

        double equity = getEquity();

        // Update peak and drawdown
        if (equity > peakEquity) {
            peakEquity = equity;
        }
        currentDrawdown = peakEquity - equity;
        double drawdownPercent = peakEquity > 0 ? (currentDrawdown / peakEquity) * 100 : 0;

        if (currentDrawdown > maxDrawdown) {
            maxDrawdown = currentDrawdown;
            maxDrawdownPercent = drawdownPercent;
        }

        // Record equity point
        equityHistory.add(new EquityPoint(bar.timestamp(), equity, currentDrawdown, hasPosition()));
    }

    /**
     * Get all completed trades.
     */
    public List<Trade> getTrades() {
        return Collections.unmodifiableList(completedTrades);
    }

    /**
     * Get equity history.
     */
    public List<EquityPoint> getEquityHistory() {
        return Collections.unmodifiableList(equityHistory);
    }

    /**
     * Get initial cash amount.
     */
    public double getInitialCash() {
        return initialCash;
    }

    /**
     * Get peak equity.
     */
    public double getPeakEquity() {
        return peakEquity;
    }

    /**
     * Get current drawdown.
     */
    public double getCurrentDrawdown() {
        return currentDrawdown;
    }

    /**
     * Get maximum drawdown.
     */
    public double getMaxDrawdown() {
        return maxDrawdown;
    }

    /**
     * Get maximum drawdown as percentage.
     */
    public double getMaxDrawdownPercent() {
        return maxDrawdownPercent;
    }

    /**
     * Get total bars spent in market.
     */
    public int getTotalBarsInMarket() {
        return totalBarsInMarket;
    }

    /**
     * Get maximum position value held.
     */
    public double getMaxPositionValue() {
        return maxPositionValue;
    }

    /**
     * Reset portfolio to initial state.
     */
    public void reset() {
        cash = initialCash;
        currentPosition = null;
        completedTrades.clear();
        equityHistory.clear();
        peakEquity = initialCash;
        currentDrawdown = 0;
        maxDrawdown = 0;
        maxDrawdownPercent = 0;
        totalBarsInMarket = 0;
        maxPositionValue = 0;
    }

    /**
     * Equity point in time.
     */
    public record EquityPoint(long timestamp, double equity, double drawdown, boolean inPosition) {
    }

    @Override
    public String toString() {
        return String.format("Portfolio[Equity=%.2f, Cash=%.2f, Position=%s, Trades=%d]",
                getEquity(), cash, currentPosition != null ? "Yes" : "No", completedTrades.size());
    }
}
