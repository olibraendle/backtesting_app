package com.backtester.strategy.builtin;

import com.backtester.common.model.Bar;
import com.backtester.strategy.BaseStrategy;
import com.backtester.strategy.StrategyParameter;

import java.util.List;

/**
 * SMA Crossover Strategy with built-in stop loss / take profit.
 *
 * Strategy has FULL control:
 * - Entry: When fast SMA crosses above slow SMA
 * - Exit: When fast SMA crosses below slow SMA, OR stop loss hit, OR take profit hit
 * - Position sizing: Risk-based (risks X% of equity per trade)
 */
public class SmaStrategy extends BaseStrategy {

    // Parameters
    private int fastPeriod;
    private int slowPeriod;
    private double riskPercent;
    private double stopLossAtr;
    private double takeProfitAtr;

    // State tracking (strategy maintains its own state)
    private double entryPrice;
    private double stopLoss;
    private double takeProfit;
    private double prevFastSma;
    private double prevSlowSma;

    @Override
    public String getName() {
        return "SMA Crossover";
    }

    @Override
    public String getDescription() {
        return "Enters long when fast SMA crosses above slow SMA. " +
               "Includes ATR-based stop loss and take profit managed by the strategy.";
    }

    @Override
    public List<StrategyParameter> getParameters() {
        return List.of(
                StrategyParameter.intParam("fastPeriod", "Fast SMA period", 10, 5, 50, 1),
                StrategyParameter.intParam("slowPeriod", "Slow SMA period", 20, 10, 200, 1),
                StrategyParameter.doubleParam("riskPercent", "Risk per trade (%)", 2.0, 0.5, 10.0, 0.5),
                StrategyParameter.doubleParam("stopLossAtr", "Stop loss (ATR multiplier)", 2.0, 0.5, 5.0, 0.5),
                StrategyParameter.doubleParam("takeProfitAtr", "Take profit (ATR multiplier)", 3.0, 1.0, 10.0, 0.5)
        );
    }

    @Override
    public int getWarmupBars() {
        return Math.max(getIntParam("slowPeriod"), 14) + 1;
    }

    @Override
    protected void onInitialize() {
        fastPeriod = getIntParam("fastPeriod");
        slowPeriod = getIntParam("slowPeriod");
        riskPercent = getDoubleParam("riskPercent");
        stopLossAtr = getDoubleParam("stopLossAtr");
        takeProfitAtr = getDoubleParam("takeProfitAtr");

        // Reset state
        entryPrice = 0;
        stopLoss = 0;
        takeProfit = 0;
        prevFastSma = Double.NaN;
        prevSlowSma = Double.NaN;
    }

    @Override
    public void onBar(Bar bar) {
        double fastSma = sma(fastPeriod);
        double slowSma = sma(slowPeriod);
        double atrValue = atr(14);

        // Skip if indicators not ready
        if (Double.isNaN(fastSma) || Double.isNaN(slowSma) || Double.isNaN(atrValue)) {
            prevFastSma = fastSma;
            prevSlowSma = slowSma;
            return;
        }

        double close = bar.close();
        double high = bar.high();
        double low = bar.low();

        if (hasPosition()) {
            // === STRATEGY CONTROLS EXIT LOGIC ===

            // Check stop loss (hit during bar)
            if (low <= stopLoss) {
                double fillPrice = closePositionAt(stopLoss);
                if (!Double.isNaN(fillPrice)) {
                    logTrade("STOP LOSS", getPositionSize(), fillPrice,
                            String.format("SL hit at %.5f", stopLoss));
                }
            }
            // Check take profit (hit during bar)
            else if (high >= takeProfit) {
                double fillPrice = closePositionAt(takeProfit);
                if (!Double.isNaN(fillPrice)) {
                    logTrade("TAKE PROFIT", getPositionSize(), fillPrice,
                            String.format("TP hit at %.5f", takeProfit));
                }
            }
            // Check signal-based exit (SMA cross down)
            else if (!Double.isNaN(prevFastSma) && prevFastSma >= prevSlowSma && fastSma < slowSma) {
                double fillPrice = closePosition();
                logTrade("EXIT", getPositionSize(), fillPrice, "SMA cross down");
            }

        } else {
            // === STRATEGY CONTROLS ENTRY LOGIC ===

            // Check for bullish crossover
            if (!Double.isNaN(prevFastSma) && !Double.isNaN(prevSlowSma) &&
                    prevFastSma <= prevSlowSma && fastSma > slowSma) {

                // Calculate position size based on risk
                double stopDistance = atrValue * stopLossAtr;
                double quantity = quantityForRisk(riskPercent, stopDistance);

                if (quantity > 0) {
                    // Execute entry
                    double fillPrice = buy(quantity);

                    // Set stop loss and take profit (strategy tracks these)
                    entryPrice = fillPrice;
                    stopLoss = fillPrice - stopDistance;
                    takeProfit = fillPrice + atrValue * takeProfitAtr;

                    logTrade("ENTRY", quantity, fillPrice,
                            String.format("SMA cross up, SL=%.5f, TP=%.5f", stopLoss, takeProfit));
                }
            }
        }

        // Update previous values for next bar
        prevFastSma = fastSma;
        prevSlowSma = slowSma;
    }
}
