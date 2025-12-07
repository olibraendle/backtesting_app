package com.backtester.strategy.builtin;

import com.backtester.common.model.Bar;
import com.backtester.strategy.BaseStrategy;
import com.backtester.strategy.StrategyParameter;

import java.util.List;

/**
 * My Custom Strategy Template
 *
 * This is a starting template for developing your own strategy.
 * Modify the parameters, entry/exit logic as needed.
 */
public class MyStrategy extends BaseStrategy {

    // Strategy parameters (will be configurable in UI)
    private int lookbackPeriod;
    private double riskPercent;
    private double stopLossPercent;
    private double takeProfitPercent;

    // State variables (reset each backtest)
    private double entryPrice = 0;

    @Override
    public String getName() {
        return "My Strategy";
    }

    @Override
    public String getDescription() {
        return "Custom strategy template - modify to implement your trading logic.";
    }

    @Override
    public List<StrategyParameter> getParameters() {
        return List.of(
            // Add your configurable parameters here
            StrategyParameter.intParam("lookbackPeriod", "Lookback period for signals", 20, 5, 100, 5),
            StrategyParameter.doubleParam("riskPercent", "Risk per trade (%)", 2.0, 0.5, 10.0, 0.5),
            StrategyParameter.doubleParam("stopLossPercent", "Stop loss (%)", 2.0, 0.5, 10.0, 0.5),
            StrategyParameter.doubleParam("takeProfitPercent", "Take profit (%)", 4.0, 1.0, 20.0, 1.0)
        );
    }

    @Override
    public int getWarmupBars() {
        // Return enough bars for your indicators to calculate
        return getIntParam("lookbackPeriod") + 10;
    }

    @Override
    protected void onInitialize() {
        // Load parameters
        lookbackPeriod = getIntParam("lookbackPeriod");
        riskPercent = getDoubleParam("riskPercent");
        stopLossPercent = getDoubleParam("stopLossPercent");
        takeProfitPercent = getDoubleParam("takeProfitPercent");

        // Reset state
        entryPrice = 0;
    }

    @Override
    public void onBar(Bar bar) {
        double close = bar.close();

        // ============================================
        // AVAILABLE INDICATORS (call these methods):
        // ============================================
        // sma(period)        - Simple Moving Average
        // ema(period)        - Exponential Moving Average
        // rsi(period)        - Relative Strength Index (0-100)
        // atr(period)        - Average True Range
        // highest(period)    - Highest high over period
        // lowest(period)     - Lowest low over period
        // stdDev(period)     - Standard Deviation
        // macd(fast, slow)   - MACD line
        // macdSignal(f,s,sig)- MACD signal line
        // bollingerUpper(period, stdDevs)
        // bollingerLower(period, stdDevs)
        // momentum(period)   - Price momentum
        // roc(period)        - Rate of Change (%)
        // adx(period)        - Average Directional Index
        // cci(period)        - Commodity Channel Index
        // williamsR(period)  - Williams %R
        // stochK(period)     - Stochastic %K
        // stochD(period, smoothing) - Stochastic %D

        // ============================================
        // AVAILABLE POSITION INFO:
        // ============================================
        // hasPosition()      - true if in a trade
        // isLong()           - true if long position
        // isShort()          - true if short position
        // getPositionSize()  - current position quantity
        // getPositionEntryPrice() - entry price
        // getUnrealizedPnL() - current unrealized P&L
        // getEquity()        - current account equity
        // getCash()          - available cash

        // ============================================
        // AVAILABLE ORDER METHODS:
        // ============================================
        // buy(quantity)      - market buy
        // sell(quantity)     - market sell
        // closePosition()    - close current position
        // quantityForPercent(pct) - calc qty for % of equity
        // quantityForRisk(riskPct, stopDistance) - risk-based sizing

        // ============================================
        // YOUR STRATEGY LOGIC GOES HERE
        // ============================================

        if (hasPosition()) {
            // --- EXIT LOGIC ---

            // Example: Simple stop loss / take profit
            double pnlPercent = ((close - entryPrice) / entryPrice) * 100;

            if (pnlPercent <= -stopLossPercent) {
                closePosition();
                entryPrice = 0;
            } else if (pnlPercent >= takeProfitPercent) {
                closePosition();
                entryPrice = 0;
            }

            // Add your own exit conditions here...

        } else {
            // --- ENTRY LOGIC ---

            // Example: Buy when price crosses above SMA
            double smaValue = sma(lookbackPeriod);
            if (!Double.isNaN(smaValue) && close > smaValue) {

                // Calculate position size based on risk
                double stopDistance = close * (stopLossPercent / 100.0);
                double quantity = quantityForRisk(riskPercent, stopDistance);

                // Round to whole contracts for futures
                quantity = Math.floor(quantity);

                if (quantity >= 1) {
                    buy(quantity);
                    entryPrice = close;
                }
            }

            // Add your own entry conditions here...
        }
    }

    @Override
    public void onEnd() {
        // Close any remaining position at end of backtest
        if (hasPosition()) {
            closePosition();
        }
    }
}
