package com.backtester.stats;

import com.backtester.core.engine.BacktestResult;
import com.backtester.core.portfolio.Portfolio;
import com.backtester.core.portfolio.Trade;
import com.backtester.common.model.TimeFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates comprehensive trading statistics from backtest results.
 */
public class StatisticsCalculator {

    private static final double RISK_FREE_RATE = 0.0; // Assume 0% for simplicity
    private static final int TRADING_DAYS_PER_YEAR = 252;

    /**
     * Calculate all statistics from a backtest result.
     */
    public BacktestStatistics calculate(BacktestResult result) {
        List<Trade> trades = result.getTrades();
        List<Portfolio.EquityPoint> equityHistory = result.getEquityHistory();
        double initialEquity = result.getInitialEquity();
        double finalEquity = result.getFinalEquity();

        // Calculate returns array
        double[] returns = calculateReturns(equityHistory);

        // P&L Metrics
        double netProfit = finalEquity - initialEquity;
        double netReturnPercent = (netProfit / initialEquity) * 100;
        double grossProfit = trades.stream().filter(Trade::isWin).mapToDouble(Trade::netPnl).sum();
        double grossLoss = Math.abs(trades.stream().filter(Trade::isLoss).mapToDouble(Trade::netPnl).sum());
        double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : (grossProfit > 0 ? Double.POSITIVE_INFINITY : 0);

        // Run-up
        double maxEquityRunUp = calculateMaxRunUp(equityHistory, initialEquity);
        double maxEquityRunUpPercent = (maxEquityRunUp / initialEquity) * 100;

        // Drawdown
        DrawdownResult drawdown = calculateDrawdown(equityHistory);
        double maxDrawdown = drawdown.maxDrawdown;
        double maxDrawdownPercent = drawdown.maxDrawdownPercent;
        int maxDrawdownDuration = drawdown.maxDrawdownDuration;

        // Alpha
        double buyAndHoldReturn = result.getBuyAndHoldReturn();
        double alpha = netReturnPercent - buyAndHoldReturn;

        // Risk ratios
        double sharpeRatio = calculateSharpeRatio(returns);
        double sortinoRatio = calculateSortinoRatio(returns);
        double cagr = calculateCAGR(initialEquity, finalEquity, result.getTotalBars(),
                result.getDataInfo() != null ? result.getDataInfo().timeFrame() : TimeFrame.D1);
        double calmarRatio = maxDrawdownPercent != 0 ? cagr / maxDrawdownPercent : 0;
        double recoveryFactor = maxDrawdown != 0 ? netProfit / maxDrawdown : 0;

        // Trade metrics
        int totalTrades = trades.size();
        int winningTrades = (int) trades.stream().filter(Trade::isWin).count();
        int losingTrades = (int) trades.stream().filter(Trade::isLoss).count();
        double winRate = totalTrades > 0 ? ((double) winningTrades / totalTrades) * 100 : 0;

        double avgTrade = totalTrades > 0 ? netProfit / totalTrades : 0;
        double avgWin = winningTrades > 0 ? grossProfit / winningTrades : 0;
        double avgLoss = losingTrades > 0 ? grossLoss / losingTrades : 0;
        double payoffRatio = avgLoss != 0 ? avgWin / avgLoss : (avgWin > 0 ? Double.POSITIVE_INFINITY : 0);

        double expectancy = totalTrades > 0 ? netProfit / totalTrades : 0;
        double expectancyPercent = totalTrades > 0 ?
                trades.stream().mapToDouble(Trade::returnPercent).average().orElse(0) : 0;

        double largestWin = trades.stream().mapToDouble(Trade::netPnl).max().orElse(0);
        double largestLoss = trades.stream().mapToDouble(Trade::netPnl).min().orElse(0);

        double avgBarsInTrade = totalTrades > 0 ?
                trades.stream().mapToInt(Trade::barsHeld).average().orElse(0) : 0;

        ConsecutiveResult consecutive = calculateConsecutive(trades);

        // Volatility
        double returnVolatility = calculateStdDev(returns) * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100;
        double downsideDeviation = calculateDownsideDeviation(returns) * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100;
        double timeInMarket = result.getTotalBars() > 0 ?
                ((double) result.getBarsInMarket() / result.getTotalBars()) * 100 : 0;

        // Portfolio metrics
        double avgEquity = equityHistory.stream()
                .mapToDouble(Portfolio.EquityPoint::equity)
                .average().orElse(initialEquity);
        double totalTraded = trades.stream()
                .mapToDouble(t -> t.entryPrice() * t.quantity() * 2)
                .sum();
        double turnover = avgEquity > 0 ? totalTraded / avgEquity : 0;

        double totalBarsDouble = result.getTotalBars();
        int barsPerYear = result.getDataInfo() != null ?
                result.getDataInfo().timeFrame().barsPerYear() : TRADING_DAYS_PER_YEAR;
        double years = totalBarsDouble / barsPerYear;
        int tradesPerYear = years > 0 ? (int) (totalTrades / years) : totalTrades;

        // Costs
        double totalCosts = result.getTotalCommissions() + result.getTotalSlippage() + result.getTotalSpreadCost();
        double pnlBeforeCosts = netProfit + totalCosts;
        double costImpactPercent = pnlBeforeCosts != 0 ? (totalCosts / Math.abs(pnlBeforeCosts)) * 100 : 0;

        return BacktestStatistics.builder()
                .netProfit(netProfit)
                .netReturnPercent(netReturnPercent)
                .grossProfit(grossProfit)
                .grossLoss(grossLoss)
                .profitFactor(profitFactor)
                .maxEquityRunUp(maxEquityRunUp)
                .maxEquityRunUpPercent(maxEquityRunUpPercent)
                .maxDrawdown(maxDrawdown)
                .maxDrawdownPercent(maxDrawdownPercent)
                .maxDrawdownDuration(maxDrawdownDuration)
                .buyAndHoldReturn(buyAndHoldReturn)
                .alpha(alpha)
                .sharpeRatio(sharpeRatio)
                .sortinoRatio(sortinoRatio)
                .calmarRatio(calmarRatio)
                .recoveryFactor(recoveryFactor)
                .totalTrades(totalTrades)
                .winningTrades(winningTrades)
                .losingTrades(losingTrades)
                .winRate(winRate)
                .avgTrade(avgTrade)
                .avgWin(avgWin)
                .avgLoss(avgLoss)
                .payoffRatio(payoffRatio)
                .expectancy(expectancy)
                .expectancyPercent(expectancyPercent)
                .largestWin(largestWin)
                .largestLoss(largestLoss)
                .avgBarsInTrade(avgBarsInTrade)
                .maxConsecutiveWins(consecutive.maxWins)
                .maxConsecutiveLosses(consecutive.maxLosses)
                .returnVolatility(returnVolatility)
                .downsideDeviation(downsideDeviation)
                .timeInMarket(timeInMarket)
                .maxPositionValue(0) // Would need position tracking
                .avgPositionValue(0)
                .turnover(turnover)
                .tradesPerYear(tradesPerYear)
                .totalCommissions(result.getTotalCommissions())
                .totalSlippage(result.getTotalSlippage())
                .totalSpreadCost(result.getTotalSpreadCost())
                .totalCosts(totalCosts)
                .pnlBeforeCosts(pnlBeforeCosts)
                .pnlAfterCosts(netProfit)
                .costImpactPercent(costImpactPercent)
                .totalBars(result.getTotalBars())
                .barsInMarket(result.getBarsInMarket())
                .cagr(cagr)
                .build();
    }

    /**
     * Calculate period returns from equity history.
     */
    private double[] calculateReturns(List<Portfolio.EquityPoint> equityHistory) {
        if (equityHistory.size() < 2) {
            return new double[0];
        }

        double[] returns = new double[equityHistory.size() - 1];
        for (int i = 1; i < equityHistory.size(); i++) {
            double prevEquity = equityHistory.get(i - 1).equity();
            double currEquity = equityHistory.get(i).equity();
            returns[i - 1] = prevEquity > 0 ? (currEquity - prevEquity) / prevEquity : 0;
        }
        return returns;
    }

    /**
     * Calculate maximum run-up (peak equity above initial).
     */
    private double calculateMaxRunUp(List<Portfolio.EquityPoint> equityHistory, double initialEquity) {
        double maxEquity = equityHistory.stream()
                .mapToDouble(Portfolio.EquityPoint::equity)
                .max().orElse(initialEquity);
        return maxEquity - initialEquity;
    }

    /**
     * Calculate drawdown metrics.
     */
    private DrawdownResult calculateDrawdown(List<Portfolio.EquityPoint> equityHistory) {
        if (equityHistory.isEmpty()) {
            return new DrawdownResult(0, 0, 0);
        }

        double peak = equityHistory.get(0).equity();
        double maxDrawdown = 0;
        double maxDrawdownPercent = 0;
        int maxDrawdownDuration = 0;
        int currentDrawdownDuration = 0;

        for (Portfolio.EquityPoint point : equityHistory) {
            double equity = point.equity();

            if (equity > peak) {
                peak = equity;
                currentDrawdownDuration = 0;
            } else {
                double drawdown = peak - equity;
                double drawdownPercent = peak > 0 ? (drawdown / peak) * 100 : 0;
                currentDrawdownDuration++;

                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown;
                    maxDrawdownPercent = drawdownPercent;
                }
                if (currentDrawdownDuration > maxDrawdownDuration) {
                    maxDrawdownDuration = currentDrawdownDuration;
                }
            }
        }

        return new DrawdownResult(maxDrawdown, maxDrawdownPercent, maxDrawdownDuration);
    }

    /**
     * Calculate Sharpe Ratio.
     */
    private double calculateSharpeRatio(double[] returns) {
        if (returns.length == 0) return 0;

        double meanReturn = mean(returns);
        double stdDev = calculateStdDev(returns);

        if (stdDev == 0) return 0;

        // Annualized Sharpe
        return Math.sqrt(TRADING_DAYS_PER_YEAR) * (meanReturn - RISK_FREE_RATE / TRADING_DAYS_PER_YEAR) / stdDev;
    }

    /**
     * Calculate Sortino Ratio (uses downside deviation).
     */
    private double calculateSortinoRatio(double[] returns) {
        if (returns.length == 0) return 0;

        double meanReturn = mean(returns);
        double downsideDeviation = calculateDownsideDeviation(returns);

        if (downsideDeviation == 0) return 0;

        return Math.sqrt(TRADING_DAYS_PER_YEAR) * (meanReturn - RISK_FREE_RATE / TRADING_DAYS_PER_YEAR) / downsideDeviation;
    }

    /**
     * Calculate downside deviation (semi-deviation of negative returns).
     */
    private double calculateDownsideDeviation(double[] returns) {
        List<Double> negativeReturns = new ArrayList<>();
        for (double r : returns) {
            if (r < 0) {
                negativeReturns.add(r);
            }
        }

        if (negativeReturns.isEmpty()) return 0;

        double[] negArray = negativeReturns.stream().mapToDouble(d -> d).toArray();
        return calculateStdDev(negArray);
    }

    /**
     * Calculate CAGR (Compound Annual Growth Rate).
     */
    private double calculateCAGR(double initialEquity, double finalEquity, int totalBars, TimeFrame timeFrame) {
        if (totalBars == 0 || initialEquity <= 0) return 0;

        int barsPerYear = timeFrame.barsPerYear();
        double years = (double) totalBars / barsPerYear;

        if (years <= 0) return 0;

        return (Math.pow(finalEquity / initialEquity, 1.0 / years) - 1) * 100;
    }

    /**
     * Calculate consecutive wins/losses.
     */
    private ConsecutiveResult calculateConsecutive(List<Trade> trades) {
        int maxWins = 0, maxLosses = 0;
        int currentWins = 0, currentLosses = 0;

        for (Trade trade : trades) {
            if (trade.isWin()) {
                currentWins++;
                currentLosses = 0;
                maxWins = Math.max(maxWins, currentWins);
            } else if (trade.isLoss()) {
                currentLosses++;
                currentWins = 0;
                maxLosses = Math.max(maxLosses, currentLosses);
            }
        }

        return new ConsecutiveResult(maxWins, maxLosses);
    }

    /**
     * Calculate mean of array.
     */
    private double mean(double[] values) {
        if (values.length == 0) return 0;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    /**
     * Calculate standard deviation.
     */
    private double calculateStdDev(double[] values) {
        if (values.length < 2) return 0;

        double mean = mean(values);
        double sumSquares = 0;
        for (double v : values) {
            double diff = v - mean;
            sumSquares += diff * diff;
        }
        return Math.sqrt(sumSquares / (values.length - 1));
    }

    private record DrawdownResult(double maxDrawdown, double maxDrawdownPercent, int maxDrawdownDuration) {}
    private record ConsecutiveResult(int maxWins, int maxLosses) {}
}
