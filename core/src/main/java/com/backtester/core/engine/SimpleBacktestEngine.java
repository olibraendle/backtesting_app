package com.backtester.core.engine;

import com.backtester.core.portfolio.Portfolio;
import com.backtester.common.model.Bar;
import com.backtester.data.model.DataInfo;
import com.backtester.common.model.TimeSeries;
import com.backtester.common.strategy.ExecutableStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Simplified backtesting engine that gives strategies FULL control.
 *
 * EXECUTION MODEL: Close-to-Close
 * ===============================
 * This backtester uses a "close-to-close" execution model:
 * - Indicators are computed using data up to and including current bar's close
 * - Trades execute at the current bar's close price (plus spread/slippage)
 *
 * This is an OPTIMISTIC model because in reality:
 * - You cannot know the close price until the bar is complete
 * - Real execution would occur at next bar's open
 *
 * This model is useful for:
 * - Initial strategy testing and comparison
 * - Understanding strategy behavior
 * - Quick iteration during development
 *
 * For more realistic results, consider that actual trading would have:
 * - Execution at next bar's open (with gap risk)
 * - Slightly worse fills due to timing uncertainty
 *
 * The engine only:
 * - Iterates through bars
 * - Provides execution context to strategy
 * - Tracks equity and statistics
 *
 * Strategies control:
 * - All order logic (market, limit, stop)
 * - Stop loss / take profit logic
 * - Position sizing
 * - Entry/exit timing
 */
public class SimpleBacktestEngine {

    private final BacktestConfig config;

    public SimpleBacktestEngine(BacktestConfig config) {
        this.config = config;
    }

    /**
     * Run a backtest with the given strategy and data.
     */
    public BacktestResult run(ExecutableStrategy strategy, TimeSeries data) {
        long startTime = System.currentTimeMillis();

        // Initialize portfolio
        Portfolio portfolio = new Portfolio(config.initialCapital());

        // Create execution context
        DefaultStrategyExecutionContext context = new DefaultStrategyExecutionContext(
                data, portfolio, config
        );

        // Initialize strategy
        strategy.initialize(context);

        int warmupBars = Math.max(config.warmupBars(), strategy.getWarmupBars());

        // Main simulation loop - strategy has FULL control
        for (int i = 0; i < data.size(); i++) {
            Bar bar = data.get(i);
            context.setCurrentBarIndex(i);

            // Update portfolio with current prices (for equity tracking)
            context.updatePortfolio(bar);

            // Skip strategy execution during warmup
            if (i >= warmupBars) {
                // Let strategy do EVERYTHING
                strategy.onBar(context);
            }

            // Update previous bar references for spread/slippage models
            // This ensures dynamic spread/slippage use PREVIOUS bar data (avoids lookahead)
            config.spreadModel().updatePreviousBar(bar);
            config.slippageModel().updatePreviousBar(bar);
        }

        // Close any remaining position at market
        if (portfolio.hasPosition()) {
            context.closePosition();
        }

        // Notify strategy of end
        strategy.onEnd(context);

        long executionTime = System.currentTimeMillis() - startTime;

        // Calculate Buy & Hold return and equity history
        double buyAndHoldReturn = calculateBuyAndHoldReturn(data);
        double buyAndHoldFinalEquity = config.initialCapital() * (1 + buyAndHoldReturn / 100.0);
        List<Double> buyAndHoldEquityHistory = calculateBuyAndHoldEquityHistory(data, config.initialCapital());

        // Build result
        return BacktestResult.builder()
                .strategyName(strategy.getName())
                .config(config)
                .dataInfo(DataInfo.from(data, null))
                .runTime(LocalDateTime.now())
                .initialEquity(config.initialCapital())
                .finalEquity(portfolio.getEquity())
                .trades(portfolio.getTrades())
                .equityHistory(portfolio.getEquityHistory())
                .buyAndHoldEquityHistory(buyAndHoldEquityHistory)
                .buyAndHoldReturn(buyAndHoldReturn)
                .buyAndHoldFinalEquity(buyAndHoldFinalEquity)
                .totalCommissions(context.getTotalCommissions())
                .totalSlippage(context.getTotalSlippage())
                .totalSpreadCost(context.getTotalSpreadCost())
                .totalBars(data.size())
                .barsInMarket(portfolio.getTotalBarsInMarket())
                .executionTimeMs(executionTime)
                .build();
    }

    /**
     * Calculate Buy & Hold return percentage.
     */
    private double calculateBuyAndHoldReturn(TimeSeries data) {
        if (data.isEmpty()) return 0;
        double startPrice = data.getFirst().close();
        double endPrice = data.getLast().close();
        return ((endPrice - startPrice) / startPrice) * 100;
    }

    /**
     * Calculate Buy & Hold equity history - simulating holding from the start.
     */
    private List<Double> calculateBuyAndHoldEquityHistory(TimeSeries data, double initialCapital) {
        List<Double> history = new ArrayList<>();
        if (data.isEmpty()) return history;

        double startPrice = data.getFirst().close();
        double shares = initialCapital / startPrice;

        for (int i = 0; i < data.size(); i++) {
            double equity = shares * data.get(i).close();
            history.add(equity);
        }
        return history;
    }
}
