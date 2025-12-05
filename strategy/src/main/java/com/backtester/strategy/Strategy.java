package com.backtester.strategy;

import com.backtester.common.model.Bar;
import com.backtester.common.strategy.StrategyExecutionContext;

import java.util.List;
import java.util.Map;

/**
 * Base interface for all trading strategies.
 * Strategies implement trading logic that generates buy/sell signals.
 */
public interface Strategy {

    /**
     * Get the strategy name.
     */
    String getName();

    /**
     * Get a description of the strategy.
     */
    String getDescription();

    /**
     * Get the list of configurable parameters.
     */
    List<StrategyParameter> getParameters();

    /**
     * Set parameter values.
     */
    void setParameters(Map<String, Object> parameters);

    /**
     * Get current parameter values.
     */
    Map<String, Object> getParameterValues();

    /**
     * Initialize the strategy with the trading context.
     * Called once before the backtest begins.
     */
    void initialize(StrategyExecutionContext context);

    /**
     * Called for each bar during the backtest.
     * This is where trading logic should be implemented.
     */
    void onBar(Bar bar);

    /**
     * Called when the backtest ends.
     * Use for cleanup or final trades.
     */
    default void onEnd() {
        // Default: do nothing
    }

    /**
     * Get the minimum number of bars required for warmup.
     * The strategy won't receive onBar calls until this many bars have passed.
     */
    default int getWarmupBars() {
        return 0;
    }

    /**
     * Create a copy of this strategy with the same parameters.
     * Used for parallel optimization.
     */
    default Strategy copy() {
        throw new UnsupportedOperationException("Copy not implemented for " + getName());
    }
}
