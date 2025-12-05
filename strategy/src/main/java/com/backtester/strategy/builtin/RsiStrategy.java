package com.backtester.strategy.builtin;

import com.backtester.common.model.Bar;
import com.backtester.strategy.BaseStrategy;
import com.backtester.strategy.StrategyParameter;

import java.util.List;

/**
 * RSI Mean Reversion Strategy.
 *
 * Buy when RSI drops below oversold level.
 * Sell when RSI rises above overbought level.
 */
public class RsiStrategy extends BaseStrategy {

    private static final String RSI_PERIOD = "rsiPeriod";
    private static final String OVERSOLD = "oversold";
    private static final String OVERBOUGHT = "overbought";
    private static final String POSITION_SIZE = "positionSize";

    @Override
    public String getName() {
        return "RSI Mean Reversion";
    }

    @Override
    public String getDescription() {
        return "Mean reversion strategy using RSI. " +
               "Buys when RSI is oversold, sells when overbought.";
    }

    @Override
    public List<StrategyParameter> getParameters() {
        return List.of(
                StrategyParameter.intParam(RSI_PERIOD, "RSI calculation period", 14, 2, 50),
                StrategyParameter.doubleParam(OVERSOLD, "Oversold threshold", 30, 10, 40, 5),
                StrategyParameter.doubleParam(OVERBOUGHT, "Overbought threshold", 70, 60, 90, 5),
                StrategyParameter.doubleParam(POSITION_SIZE, "Position size (% of equity)", 100, 10, 100, 10)
        );
    }

    @Override
    public int getWarmupBars() {
        return getIntParam(RSI_PERIOD) + 1;
    }

    @Override
    public void onBar(Bar bar) {
        int rsiPeriod = getIntParam(RSI_PERIOD);
        double oversold = getDoubleParam(OVERSOLD);
        double overbought = getDoubleParam(OVERBOUGHT);
        double posSize = getDoubleParam(POSITION_SIZE);

        double currentRsi = rsi(rsiPeriod);

        // Skip if RSI not ready
        if (Double.isNaN(currentRsi)) {
            return;
        }

        // Trading logic
        if (!hasPosition()) {
            // Look for entry
            if (currentRsi < oversold) {
                // Oversold - buy
                double qty = quantityForPercent(posSize);
                buy(qty);
            }
        } else if (isLong()) {
            // Look for exit
            if (currentRsi > overbought) {
                // Overbought - sell
                closePosition();
            }
        }
    }

    @Override
    public void onEnd() {
        if (hasPosition()) {
            closePosition();
        }
    }
}
