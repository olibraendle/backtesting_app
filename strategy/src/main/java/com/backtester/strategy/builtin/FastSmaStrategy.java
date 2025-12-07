package com.backtester.strategy.builtin;

import com.backtester.common.model.Bar;
import com.backtester.strategy.BaseStrategy;
import com.backtester.strategy.StrategyParameter;

import java.util.List;

/**
 * Fast SMA Crossover Strategy - designed for high trade frequency.
 *
 * Uses short SMA periods to generate many trades for backtester testing.
 * - Enter long when fast SMA crosses above slow SMA
 * - Exit when fast SMA crosses below slow SMA
 * - No stop loss or take profit (pure signal-based)
 */
public class FastSmaStrategy extends BaseStrategy {

    private int fastPeriod;
    private int slowPeriod;
    private double positionSizePercent;

    private double prevFastSma = Double.NaN;
    private double prevSlowSma = Double.NaN;

    @Override
    public String getName() {
        return "Fast SMA Test";
    }

    @Override
    public String getDescription() {
        return "Fast SMA crossover for testing. Uses short periods (3/7) to generate 1000+ trades.";
    }

    @Override
    public List<StrategyParameter> getParameters() {
        return List.of(
                StrategyParameter.intParam("fastPeriod", "Fast SMA period", 3, 2, 20, 1),
                StrategyParameter.intParam("slowPeriod", "Slow SMA period", 7, 3, 50, 1),
                StrategyParameter.doubleParam("positionSize", "Position size (% of equity)", 100, 10, 100, 10)
        );
    }

    @Override
    public int getWarmupBars() {
        return getIntParam("slowPeriod") + 1;
    }

    @Override
    protected void onInitialize() {
        fastPeriod = getIntParam("fastPeriod");
        slowPeriod = getIntParam("slowPeriod");
        positionSizePercent = getDoubleParam("positionSize");

        prevFastSma = Double.NaN;
        prevSlowSma = Double.NaN;
    }

    @Override
    public void onBar(Bar bar) {
        double fastSma = sma(fastPeriod);
        double slowSma = sma(slowPeriod);

        // Skip if indicators not ready
        if (Double.isNaN(fastSma) || Double.isNaN(slowSma)) {
            prevFastSma = fastSma;
            prevSlowSma = slowSma;
            return;
        }

        // Detect crossovers
        boolean bullishCross = !Double.isNaN(prevFastSma) && !Double.isNaN(prevSlowSma) &&
                prevFastSma <= prevSlowSma && fastSma > slowSma;
        boolean bearishCross = !Double.isNaN(prevFastSma) && !Double.isNaN(prevSlowSma) &&
                prevFastSma >= prevSlowSma && fastSma < slowSma;

        if (hasPosition()) {
            // Check for bearish crossover - exit
            if (bearishCross) {
                closePosition();
            }
        } else {
            // Check for bullish crossover - enter
            if (bullishCross) {
                double quantity = quantityForPercent(positionSizePercent);
                if (quantity > 0) {
                    buy(quantity);
                }
            }
        }

        prevFastSma = fastSma;
        prevSlowSma = slowSma;
    }

    @Override
    public void onEnd() {
        if (hasPosition()) {
            closePosition();
        }
    }
}
