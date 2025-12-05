package com.backtester.core.market;

import com.backtester.core.order.Fill;
import com.backtester.core.order.Order;
import com.backtester.core.order.OrderAction;
import com.backtester.common.model.Bar;

/**
 * Simulates market execution including commission, spread, and slippage.
 */
public class MarketSimulator {
    private final CommissionModel commissionModel;
    private final SpreadModel spreadModel;
    private final SlippageModel slippageModel;

    // Statistics tracking
    private double totalCommissions;
    private double totalSlippage;
    private double totalSpreadCost;
    private int totalFills;

    public MarketSimulator(CommissionModel commissionModel,
                           SpreadModel spreadModel,
                           SlippageModel slippageModel) {
        this.commissionModel = commissionModel;
        this.spreadModel = spreadModel;
        this.slippageModel = slippageModel;
        this.totalCommissions = 0;
        this.totalSlippage = 0;
        this.totalSpreadCost = 0;
        this.totalFills = 0;
    }

    /**
     * Create a default market simulator with reasonable defaults.
     */
    public static MarketSimulator createDefault() {
        return new MarketSimulator(
                CommissionModel.percentage(0.1),    // 0.1% commission
                SpreadModel.percentage(0.01),       // 0.01% spread
                SlippageModel.fixedPercent(0.05)    // 0.05% slippage
        );
    }

    /**
     * Create a zero-cost simulator (ideal conditions).
     */
    public static MarketSimulator zeroCost() {
        return new MarketSimulator(
                CommissionModel.zero(),
                SpreadModel.zero(),
                SlippageModel.zero()
        );
    }

    /**
     * Simulate order execution and return a Fill.
     *
     * @param order        The order to execute
     * @param bar          Current market bar
     * @param requestedPrice Requested execution price (e.g., limit price or market price)
     * @return Fill with adjusted price and costs
     */
    public Fill simulateFill(Order order, Bar bar, double requestedPrice) {
        boolean isBuy = order.getAction() == OrderAction.BUY;
        double quantity = order.getQuantity();

        // 1. Apply spread (buy at ask, sell at bid)
        double spreadAdjustedPrice = isBuy
                ? spreadModel.getAskPrice(requestedPrice, bar)
                : spreadModel.getBidPrice(requestedPrice, bar);

        double spreadCost = Math.abs(spreadAdjustedPrice - requestedPrice) * quantity;

        // 2. Apply slippage
        double slippage = slippageModel.calculate(spreadAdjustedPrice, quantity, bar, isBuy);
        double finalPrice = isBuy
                ? spreadAdjustedPrice + slippage
                : spreadAdjustedPrice - slippage;

        double slippageCost = slippage * quantity;

        // 3. Calculate commission
        double commission = commissionModel.calculate(quantity, finalPrice);

        // Update statistics
        totalCommissions += commission;
        totalSlippage += slippageCost;
        totalSpreadCost += spreadCost;
        totalFills++;

        return new Fill(
                order.getId(),
                order.getSymbol(),
                order.getAction(),
                quantity,
                finalPrice,
                commission,
                slippageCost,
                bar.timestamp(),
                bar.index()
        );
    }

    /**
     * Get the execution price for a market order.
     */
    public double getMarketExecutionPrice(Bar bar, boolean isBuy) {
        // Use close price as base, apply spread
        double basePrice = bar.close();
        return isBuy
                ? spreadModel.getAskPrice(basePrice, bar)
                : spreadModel.getBidPrice(basePrice, bar);
    }

    /**
     * Calculate total costs for a hypothetical trade.
     */
    public TradeCosts calculateCosts(double price, double quantity, Bar bar, boolean isBuy) {
        double spreadCost = spreadModel.calculateHalfSpread(price, bar) * quantity;
        double slippageCost = slippageModel.calculate(price, quantity, bar, isBuy) * quantity;
        double commission = commissionModel.calculate(quantity, price);
        double totalCost = spreadCost + slippageCost + commission;
        double costPercent = (totalCost / (price * quantity)) * 100;

        return new TradeCosts(spreadCost, slippageCost, commission, totalCost, costPercent);
    }

    // ===== Getters =====

    public CommissionModel getCommissionModel() {
        return commissionModel;
    }

    public SpreadModel getSpreadModel() {
        return spreadModel;
    }

    public SlippageModel getSlippageModel() {
        return slippageModel;
    }

    public double getTotalCommissions() {
        return totalCommissions;
    }

    public double getTotalSlippage() {
        return totalSlippage;
    }

    public double getTotalSpreadCost() {
        return totalSpreadCost;
    }

    public double getTotalCosts() {
        return totalCommissions + totalSlippage + totalSpreadCost;
    }

    public int getTotalFills() {
        return totalFills;
    }

    /**
     * Reset statistics.
     */
    public void reset() {
        totalCommissions = 0;
        totalSlippage = 0;
        totalSpreadCost = 0;
        totalFills = 0;
    }

    /**
     * Trade costs breakdown.
     */
    public record TradeCosts(
            double spreadCost,
            double slippageCost,
            double commission,
            double totalCost,
            double costPercent
    ) {
        @Override
        public String toString() {
            return String.format("Costs[Spread=%.2f, Slip=%.2f, Comm=%.2f, Total=%.2f (%.3f%%)]",
                    spreadCost, slippageCost, commission, totalCost, costPercent);
        }
    }

    @Override
    public String toString() {
        return String.format("MarketSimulator[Commission=%s, Spread=%s, Slippage=%s]",
                commissionModel, spreadModel, slippageModel);
    }
}
