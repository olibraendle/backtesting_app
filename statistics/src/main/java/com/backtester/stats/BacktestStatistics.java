package com.backtester.stats;

/**
 * Comprehensive statistics from a backtest run.
 * Contains all metrics for evaluating trading strategy performance.
 */
public record BacktestStatistics(
        // ===== P&L Metrics =====
        double netProfit,
        double netReturnPercent,
        double grossProfit,
        double grossLoss,
        double profitFactor,
        double maxEquityRunUp,
        double maxEquityRunUpPercent,

        // ===== Risk Metrics =====
        double maxDrawdown,
        double maxDrawdownPercent,
        int maxDrawdownDuration,        // in bars
        double buyAndHoldReturn,
        double alpha,                   // Return vs Buy&Hold
        double sharpeRatio,
        double sortinoRatio,
        double calmarRatio,             // CAGR / Max DD
        double recoveryFactor,          // Net profit / Max DD

        // ===== Trade Metrics =====
        int totalTrades,
        int winningTrades,
        int losingTrades,
        double winRate,
        double avgTrade,
        double avgWin,
        double avgLoss,
        double payoffRatio,             // Avg win / Avg loss
        double expectancy,              // Expected $ per trade
        double expectancyPercent,       // Expected % per trade
        double largestWin,
        double largestLoss,
        double avgBarsInTrade,
        int maxConsecutiveWins,
        int maxConsecutiveLosses,

        // ===== Volatility Metrics =====
        double returnVolatility,        // Std dev of returns
        double downsideDeviation,
        double timeInMarket,            // % of time with position

        // ===== Portfolio Metrics =====
        double maxPositionValue,
        double avgPositionValue,
        double turnover,                // Total traded / avg equity
        int tradesPerYear,

        // ===== Cost Analysis =====
        double totalCommissions,
        double totalSlippage,
        double totalSpreadCost,
        double totalCosts,
        double pnlBeforeCosts,
        double pnlAfterCosts,
        double costImpactPercent,       // How much costs reduced returns

        // ===== Time Analysis =====
        int totalBars,
        int barsInMarket,
        double cagr                     // Compound Annual Growth Rate
) {
    /**
     * Check if the strategy is profitable.
     */
    public boolean isProfitable() {
        return netProfit > 0;
    }

    /**
     * Check if the strategy beats Buy & Hold.
     */
    public boolean beatsMarket() {
        return netReturnPercent > buyAndHoldReturn;
    }

    /**
     * Get a quick summary string.
     */
    public String getSummary() {
        return String.format(
                "Return: %.2f%% | Alpha: %.2f%% | Sharpe: %.2f | Win Rate: %.1f%% | Trades: %d | Max DD: %.2f%%",
                netReturnPercent, alpha, sharpeRatio, winRate, totalTrades, maxDrawdownPercent
        );
    }

    /**
     * Builder for creating BacktestStatistics.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double netProfit;
        private double netReturnPercent;
        private double grossProfit;
        private double grossLoss;
        private double profitFactor;
        private double maxEquityRunUp;
        private double maxEquityRunUpPercent;
        private double maxDrawdown;
        private double maxDrawdownPercent;
        private int maxDrawdownDuration;
        private double buyAndHoldReturn;
        private double alpha;
        private double sharpeRatio;
        private double sortinoRatio;
        private double calmarRatio;
        private double recoveryFactor;
        private int totalTrades;
        private int winningTrades;
        private int losingTrades;
        private double winRate;
        private double avgTrade;
        private double avgWin;
        private double avgLoss;
        private double payoffRatio;
        private double expectancy;
        private double expectancyPercent;
        private double largestWin;
        private double largestLoss;
        private double avgBarsInTrade;
        private int maxConsecutiveWins;
        private int maxConsecutiveLosses;
        private double returnVolatility;
        private double downsideDeviation;
        private double timeInMarket;
        private double maxPositionValue;
        private double avgPositionValue;
        private double turnover;
        private int tradesPerYear;
        private double totalCommissions;
        private double totalSlippage;
        private double totalSpreadCost;
        private double totalCosts;
        private double pnlBeforeCosts;
        private double pnlAfterCosts;
        private double costImpactPercent;
        private int totalBars;
        private int barsInMarket;
        private double cagr;

        public Builder netProfit(double v) { this.netProfit = v; return this; }
        public Builder netReturnPercent(double v) { this.netReturnPercent = v; return this; }
        public Builder grossProfit(double v) { this.grossProfit = v; return this; }
        public Builder grossLoss(double v) { this.grossLoss = v; return this; }
        public Builder profitFactor(double v) { this.profitFactor = v; return this; }
        public Builder maxEquityRunUp(double v) { this.maxEquityRunUp = v; return this; }
        public Builder maxEquityRunUpPercent(double v) { this.maxEquityRunUpPercent = v; return this; }
        public Builder maxDrawdown(double v) { this.maxDrawdown = v; return this; }
        public Builder maxDrawdownPercent(double v) { this.maxDrawdownPercent = v; return this; }
        public Builder maxDrawdownDuration(int v) { this.maxDrawdownDuration = v; return this; }
        public Builder buyAndHoldReturn(double v) { this.buyAndHoldReturn = v; return this; }
        public Builder alpha(double v) { this.alpha = v; return this; }
        public Builder sharpeRatio(double v) { this.sharpeRatio = v; return this; }
        public Builder sortinoRatio(double v) { this.sortinoRatio = v; return this; }
        public Builder calmarRatio(double v) { this.calmarRatio = v; return this; }
        public Builder recoveryFactor(double v) { this.recoveryFactor = v; return this; }
        public Builder totalTrades(int v) { this.totalTrades = v; return this; }
        public Builder winningTrades(int v) { this.winningTrades = v; return this; }
        public Builder losingTrades(int v) { this.losingTrades = v; return this; }
        public Builder winRate(double v) { this.winRate = v; return this; }
        public Builder avgTrade(double v) { this.avgTrade = v; return this; }
        public Builder avgWin(double v) { this.avgWin = v; return this; }
        public Builder avgLoss(double v) { this.avgLoss = v; return this; }
        public Builder payoffRatio(double v) { this.payoffRatio = v; return this; }
        public Builder expectancy(double v) { this.expectancy = v; return this; }
        public Builder expectancyPercent(double v) { this.expectancyPercent = v; return this; }
        public Builder largestWin(double v) { this.largestWin = v; return this; }
        public Builder largestLoss(double v) { this.largestLoss = v; return this; }
        public Builder avgBarsInTrade(double v) { this.avgBarsInTrade = v; return this; }
        public Builder maxConsecutiveWins(int v) { this.maxConsecutiveWins = v; return this; }
        public Builder maxConsecutiveLosses(int v) { this.maxConsecutiveLosses = v; return this; }
        public Builder returnVolatility(double v) { this.returnVolatility = v; return this; }
        public Builder downsideDeviation(double v) { this.downsideDeviation = v; return this; }
        public Builder timeInMarket(double v) { this.timeInMarket = v; return this; }
        public Builder maxPositionValue(double v) { this.maxPositionValue = v; return this; }
        public Builder avgPositionValue(double v) { this.avgPositionValue = v; return this; }
        public Builder turnover(double v) { this.turnover = v; return this; }
        public Builder tradesPerYear(int v) { this.tradesPerYear = v; return this; }
        public Builder totalCommissions(double v) { this.totalCommissions = v; return this; }
        public Builder totalSlippage(double v) { this.totalSlippage = v; return this; }
        public Builder totalSpreadCost(double v) { this.totalSpreadCost = v; return this; }
        public Builder totalCosts(double v) { this.totalCosts = v; return this; }
        public Builder pnlBeforeCosts(double v) { this.pnlBeforeCosts = v; return this; }
        public Builder pnlAfterCosts(double v) { this.pnlAfterCosts = v; return this; }
        public Builder costImpactPercent(double v) { this.costImpactPercent = v; return this; }
        public Builder totalBars(int v) { this.totalBars = v; return this; }
        public Builder barsInMarket(int v) { this.barsInMarket = v; return this; }
        public Builder cagr(double v) { this.cagr = v; return this; }

        public BacktestStatistics build() {
            return new BacktestStatistics(
                    netProfit, netReturnPercent, grossProfit, grossLoss, profitFactor,
                    maxEquityRunUp, maxEquityRunUpPercent, maxDrawdown, maxDrawdownPercent,
                    maxDrawdownDuration, buyAndHoldReturn, alpha, sharpeRatio, sortinoRatio,
                    calmarRatio, recoveryFactor, totalTrades, winningTrades, losingTrades,
                    winRate, avgTrade, avgWin, avgLoss, payoffRatio, expectancy,
                    expectancyPercent, largestWin, largestLoss, avgBarsInTrade,
                    maxConsecutiveWins, maxConsecutiveLosses, returnVolatility,
                    downsideDeviation, timeInMarket, maxPositionValue, avgPositionValue,
                    turnover, tradesPerYear, totalCommissions, totalSlippage, totalSpreadCost,
                    totalCosts, pnlBeforeCosts, pnlAfterCosts, costImpactPercent,
                    totalBars, barsInMarket, cagr
            );
        }
    }
}
