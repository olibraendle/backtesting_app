package com.backtester.strategy.builtin;

import com.backtester.common.model.Bar;
import com.backtester.strategy.BaseStrategy;
import com.backtester.strategy.StrategyParameter;

import java.util.List;

/**
 * Donchian Channel Breakout Strategy.
 *
 * Buy when price breaks above the highest high of N periods.
 * Sell when price breaks below the lowest low of N periods (or exit period).
 */
public class BreakoutStrategy extends BaseStrategy {

    private static final String ENTRY_PERIOD = "entryPeriod";
    private static final String EXIT_PERIOD = "exitPeriod";
    private static final String POSITION_SIZE = "positionSize";

    @Override
    public String getName() {
        return "Donchian Breakout";
    }

    @Override
    public String getDescription() {
        return "Trend-following breakout strategy using Donchian Channels. " +
               "Buys on new highs, exits on new lows.";
    }

    @Override
    public List<StrategyParameter> getParameters() {
        return List.of(
                StrategyParameter.intParam(ENTRY_PERIOD, "Entry period (highest high)", 20, 5, 100),
                StrategyParameter.intParam(EXIT_PERIOD, "Exit period (lowest low)", 10, 3, 50),
                StrategyParameter.doubleParam(POSITION_SIZE, "Position size (% of equity)", 100, 10, 100, 10)
        );
    }

    @Override
    public int getWarmupBars() {
        return Math.max(getIntParam(ENTRY_PERIOD), getIntParam(EXIT_PERIOD)) + 1;
    }

    @Override
    public void onBar(Bar bar) {
        int entryPeriod = getIntParam(ENTRY_PERIOD);
        int exitPeriod = getIntParam(EXIT_PERIOD);
        double posSize = getDoubleParam(POSITION_SIZE);

        // Get channel levels (excluding current bar)
        int prevIndex = getBarIndex() - 1;
        if (prevIndex < entryPeriod) return;

        double upperChannel = highestHigh(entryPeriod, prevIndex);
        double lowerChannel = lowestLow(exitPeriod, prevIndex);

        double currentHigh = bar.high();
        double currentLow = bar.low();

        // Trading logic
        if (!hasPosition()) {
            // Entry: price breaks above upper channel
            if (currentHigh > upperChannel) {
                double qty = quantityForPercent(posSize);
                buy(qty);
            }
        } else if (isLong()) {
            // Exit: price breaks below lower channel
            if (currentLow < lowerChannel) {
                closePosition();
            }
        }
    }

    private double highestHigh(int period, int endIndex) {
        double highest = Double.MIN_VALUE;
        int start = Math.max(0, endIndex - period + 1);
        for (int i = start; i <= endIndex; i++) {
            Bar b = getBar(i);
            if (b != null) {
                highest = Math.max(highest, b.high());
            }
        }
        return highest;
    }

    private double lowestLow(int period, int endIndex) {
        double lowest = Double.MAX_VALUE;
        int start = Math.max(0, endIndex - period + 1);
        for (int i = start; i <= endIndex; i++) {
            Bar b = getBar(i);
            if (b != null) {
                lowest = Math.min(lowest, b.low());
            }
        }
        return lowest;
    }

    @Override
    public void onEnd() {
        if (hasPosition()) {
            closePosition();
        }
    }
}
