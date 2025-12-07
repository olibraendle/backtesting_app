package com.backtester.core.engine;

import com.backtester.core.portfolio.Portfolio;
import com.backtester.core.portfolio.Trade;
import com.backtester.data.model.DataInfo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Results from a backtest run.
 */
public class BacktestResult {
    private final String strategyName;
    private final BacktestConfig config;
    private final DataInfo dataInfo;
    private final LocalDateTime runTime;

    // Core results
    private final double initialEquity;
    private final double finalEquity;
    private final List<Trade> trades;
    private final List<Portfolio.EquityPoint> equityHistory;

    // Buy & Hold comparison
    private final double buyAndHoldReturn;
    private final double buyAndHoldFinalEquity;
    private final List<Double> buyAndHoldEquityHistory;

    // Cost tracking
    private final double totalCommissions;
    private final double totalSlippage;
    private final double totalSpreadCost;

    // Time tracking
    private final int totalBars;
    private final int barsInMarket;
    private final long executionTimeMs;

    public BacktestResult(Builder builder) {
        this.strategyName = builder.strategyName;
        this.config = builder.config;
        this.dataInfo = builder.dataInfo;
        this.runTime = builder.runTime;
        this.initialEquity = builder.initialEquity;
        this.finalEquity = builder.finalEquity;
        this.trades = builder.trades;
        this.equityHistory = builder.equityHistory;
        this.buyAndHoldReturn = builder.buyAndHoldReturn;
        this.buyAndHoldFinalEquity = builder.buyAndHoldFinalEquity;
        this.buyAndHoldEquityHistory = builder.buyAndHoldEquityHistory;
        this.totalCommissions = builder.totalCommissions;
        this.totalSlippage = builder.totalSlippage;
        this.totalSpreadCost = builder.totalSpreadCost;
        this.totalBars = builder.totalBars;
        this.barsInMarket = builder.barsInMarket;
        this.executionTimeMs = builder.executionTimeMs;
    }

    // ===== Computed Properties =====

    /**
     * Net return percentage.
     */
    public double getNetReturnPercent() {
        return ((finalEquity - initialEquity) / initialEquity) * 100;
    }

    /**
     * Net profit in absolute terms.
     */
    public double getNetProfit() {
        return finalEquity - initialEquity;
    }

    /**
     * Total trading costs.
     */
    public double getTotalCosts() {
        return totalCommissions + totalSlippage + totalSpreadCost;
    }

    /**
     * Alpha vs Buy & Hold (in percentage points).
     */
    public double getAlpha() {
        return getNetReturnPercent() - buyAndHoldReturn;
    }

    /**
     * Time in market as percentage.
     */
    public double getTimeInMarketPercent() {
        return totalBars > 0 ? ((double) barsInMarket / totalBars) * 100 : 0;
    }

    /**
     * Number of trades.
     */
    public int getTradeCount() {
        return trades.size();
    }

    /**
     * Win rate percentage.
     */
    public double getWinRate() {
        if (trades.isEmpty()) return 0;
        long wins = trades.stream().filter(Trade::isWin).count();
        return ((double) wins / trades.size()) * 100;
    }

    // ===== Getters =====

    public String getStrategyName() {
        return strategyName;
    }

    public BacktestConfig getConfig() {
        return config;
    }

    public DataInfo getDataInfo() {
        return dataInfo;
    }

    public LocalDateTime getRunTime() {
        return runTime;
    }

    public double getInitialEquity() {
        return initialEquity;
    }

    public double getFinalEquity() {
        return finalEquity;
    }

    public List<Trade> getTrades() {
        return trades;
    }

    public List<Portfolio.EquityPoint> getEquityHistory() {
        return equityHistory;
    }

    public double getBuyAndHoldReturn() {
        return buyAndHoldReturn;
    }

    public double getBuyAndHoldFinalEquity() {
        return buyAndHoldFinalEquity;
    }

    public List<Double> getBuyAndHoldEquityHistory() {
        return buyAndHoldEquityHistory;
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

    public int getTotalBars() {
        return totalBars;
    }

    public int getBarsInMarket() {
        return barsInMarket;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    // ===== Builder =====

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String strategyName;
        private BacktestConfig config;
        private DataInfo dataInfo;
        private LocalDateTime runTime = LocalDateTime.now();
        private double initialEquity;
        private double finalEquity;
        private List<Trade> trades = List.of();
        private List<Portfolio.EquityPoint> equityHistory = List.of();
        private double buyAndHoldReturn;
        private double buyAndHoldFinalEquity;
        private List<Double> buyAndHoldEquityHistory = List.of();
        private double totalCommissions;
        private double totalSlippage;
        private double totalSpreadCost;
        private int totalBars;
        private int barsInMarket;
        private long executionTimeMs;

        public Builder strategyName(String strategyName) {
            this.strategyName = strategyName;
            return this;
        }

        public Builder config(BacktestConfig config) {
            this.config = config;
            return this;
        }

        public Builder dataInfo(DataInfo dataInfo) {
            this.dataInfo = dataInfo;
            return this;
        }

        public Builder runTime(LocalDateTime runTime) {
            this.runTime = runTime;
            return this;
        }

        public Builder initialEquity(double initialEquity) {
            this.initialEquity = initialEquity;
            return this;
        }

        public Builder finalEquity(double finalEquity) {
            this.finalEquity = finalEquity;
            return this;
        }

        public Builder trades(List<Trade> trades) {
            this.trades = trades;
            return this;
        }

        public Builder equityHistory(List<Portfolio.EquityPoint> equityHistory) {
            this.equityHistory = equityHistory;
            return this;
        }

        public Builder buyAndHoldReturn(double buyAndHoldReturn) {
            this.buyAndHoldReturn = buyAndHoldReturn;
            return this;
        }

        public Builder buyAndHoldFinalEquity(double buyAndHoldFinalEquity) {
            this.buyAndHoldFinalEquity = buyAndHoldFinalEquity;
            return this;
        }

        public Builder buyAndHoldEquityHistory(List<Double> buyAndHoldEquityHistory) {
            this.buyAndHoldEquityHistory = buyAndHoldEquityHistory;
            return this;
        }

        public Builder totalCommissions(double totalCommissions) {
            this.totalCommissions = totalCommissions;
            return this;
        }

        public Builder totalSlippage(double totalSlippage) {
            this.totalSlippage = totalSlippage;
            return this;
        }

        public Builder totalSpreadCost(double totalSpreadCost) {
            this.totalSpreadCost = totalSpreadCost;
            return this;
        }

        public Builder totalBars(int totalBars) {
            this.totalBars = totalBars;
            return this;
        }

        public Builder barsInMarket(int barsInMarket) {
            this.barsInMarket = barsInMarket;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public BacktestResult build() {
            return new BacktestResult(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "BacktestResult[%s, Return=%.2f%%, Alpha=%.2f%%, Trades=%d, WinRate=%.1f%%]",
                strategyName, getNetReturnPercent(), getAlpha(), getTradeCount(), getWinRate()
        );
    }
}
