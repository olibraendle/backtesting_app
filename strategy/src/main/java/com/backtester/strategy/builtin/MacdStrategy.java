package com.backtester.strategy.builtin;

import com.backtester.common.model.Bar;
import com.backtester.strategy.BaseStrategy;
import com.backtester.strategy.StrategyParameter;

import java.util.List;

/**
 * MACD (Moving Average Convergence Divergence) Strategy.
 *
 * Buy when MACD line crosses above signal line.
 * Sell when MACD line crosses below signal line.
 */
public class MacdStrategy extends BaseStrategy {

    private static final String FAST_PERIOD = "fastPeriod";
    private static final String SLOW_PERIOD = "slowPeriod";
    private static final String SIGNAL_PERIOD = "signalPeriod";
    private static final String POSITION_SIZE = "positionSize";

    private double previousMacd = Double.NaN;
    private double previousSignal = Double.NaN;

    @Override
    public String getName() {
        return "MACD Signal";
    }

    @Override
    public String getDescription() {
        return "MACD crossover strategy. " +
               "Buys when MACD crosses above signal line, sells when it crosses below.";
    }

    @Override
    public List<StrategyParameter> getParameters() {
        return List.of(
                StrategyParameter.intParam(FAST_PERIOD, "Fast EMA period", 12, 5, 30),
                StrategyParameter.intParam(SLOW_PERIOD, "Slow EMA period", 26, 15, 60),
                StrategyParameter.intParam(SIGNAL_PERIOD, "Signal line period", 9, 3, 20),
                StrategyParameter.doubleParam(POSITION_SIZE, "Position size (% of equity)", 100, 10, 100, 10)
        );
    }

    @Override
    public int getWarmupBars() {
        return getIntParam(SLOW_PERIOD) + getIntParam(SIGNAL_PERIOD) + 1;
    }

    @Override
    protected void onInitialize() {
        previousMacd = Double.NaN;
        previousSignal = Double.NaN;
    }

    @Override
    public void onBar(Bar bar) {
        int fastPeriod = getIntParam(FAST_PERIOD);
        int slowPeriod = getIntParam(SLOW_PERIOD);
        int signalPeriod = getIntParam(SIGNAL_PERIOD);
        double posSize = getDoubleParam(POSITION_SIZE);

        // Calculate MACD components
        double fastEma = ema(fastPeriod);
        double slowEma = ema(slowPeriod);

        if (Double.isNaN(fastEma) || Double.isNaN(slowEma)) {
            return;
        }

        double macdLine = fastEma - slowEma;

        // Calculate signal line (EMA of MACD)
        // Simplified: use running calculation
        double signalLine = calculateSignalLine(macdLine, signalPeriod);

        if (Double.isNaN(signalLine)) {
            previousMacd = macdLine;
            return;
        }

        // Check for crossover
        boolean bullishCross = !Double.isNaN(previousMacd) &&
                               previousMacd <= previousSignal &&
                               macdLine > signalLine;

        boolean bearishCross = !Double.isNaN(previousMacd) &&
                               previousMacd >= previousSignal &&
                               macdLine < signalLine;

        // Trading logic
        if (bullishCross && !hasPosition()) {
            double qty = quantityForPercent(posSize);
            buy(qty);
        } else if (bearishCross && isLong()) {
            closePosition();
        }

        // Store for next bar
        previousMacd = macdLine;
        previousSignal = signalLine;
    }

    private double calculateSignalLine(double currentMacd, int period) {
        // Simple EMA calculation for signal line
        if (Double.isNaN(previousSignal)) {
            return currentMacd; // First value
        }
        double multiplier = 2.0 / (period + 1);
        return (currentMacd - previousSignal) * multiplier + previousSignal;
    }

    @Override
    public void onEnd() {
        if (hasPosition()) {
            closePosition();
        }
    }
}
