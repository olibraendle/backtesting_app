package com.backtester.common.strategy;

/**
 * Interface for strategies that can be executed by the backtest engine.
 * Strategies implement this to receive the execution context.
 */
public interface ExecutableStrategy {
    String getName();
    String getDescription();
    int getWarmupBars();
    void initialize(StrategyExecutionContext context);
    void onBar(StrategyExecutionContext context);
    void onEnd(StrategyExecutionContext context);
}
