package com.backtester.common.strategy;

import com.backtester.common.model.Bar;
import com.backtester.common.model.TimeSeries;

import java.util.List;

/**
 * Execution context that gives strategies FULL control over trading.
 * The engine does NOT manage orders - strategies handle everything:
 * - Entry/exit timing
 * - Order types (market, limit, stop)
 * - Stop loss / take profit logic
 * - Position sizing
 *
 * The context only provides:
 * - Market data access
 * - Account state (equity, positions)
 * - Trade execution (strategies call execute methods)
 * - Technical indicators
 */
public interface StrategyExecutionContext {

    // ===== Market Data =====

    /**
     * Get the full historical data.
     */
    TimeSeries getData();

    /**
     * Get current bar being processed.
     */
    Bar getCurrentBar();

    /**
     * Get current bar index.
     */
    int getBarIndex();

    /**
     * Get bar at specific index.
     */
    Bar getBar(int index);

    /**
     * Get N previous bars (for ML features).
     */
    List<Bar> getPreviousBars(int count);

    /**
     * Get close prices as array (for ML).
     */
    double[] getCloses(int count);

    /**
     * Get OHLCV as 2D array (for ML features).
     */
    double[][] getOHLCV(int count);

    // ===== Account State =====

    /**
     * Current account equity.
     */
    double getEquity();

    /**
     * Available cash.
     */
    double getCash();

    /**
     * Current position quantity (positive=long, negative=short, 0=flat).
     */
    double getPositionSize();

    /**
     * Current position entry price (0 if flat).
     */
    double getPositionEntryPrice();

    /**
     * Is there an open position?
     */
    boolean hasPosition();

    /**
     * Is current position long?
     */
    boolean isLong();

    /**
     * Is current position short?
     */
    boolean isShort();

    /**
     * Unrealized P&L of current position.
     */
    double getUnrealizedPnL();

    // ===== Order Execution (Strategy Controls Everything) =====

    /**
     * Execute a market order immediately at current bar's close.
     * Positive quantity = buy, negative = sell.
     *
     * @param quantity Signed quantity (+ buy, - sell)
     * @return Actual fill price after slippage/spread
     */
    double executeMarketOrder(double quantity);

    /**
     * Execute at a specific price (simulates limit order filled within bar).
     * Only fills if price is within bar's range.
     *
     * @param quantity Signed quantity
     * @param price Desired execution price
     * @return Actual fill price, or NaN if not filled
     */
    double executeAtPrice(double quantity, double price);

    /**
     * Close entire position at market.
     * @return Fill price
     */
    double closePosition();

    /**
     * Close position at specific price.
     * @return Fill price, or NaN if not filled
     */
    double closePositionAtPrice(double price);

    // ===== Position Sizing Helpers =====

    /**
     * Calculate quantity for given dollar amount.
     */
    double quantityForDollars(double dollars);

    /**
     * Calculate quantity for percentage of equity.
     */
    double quantityForPercentage(double percent);

    /**
     * Calculate quantity with risk-based sizing.
     * @param riskPercent Percent of equity to risk
     * @param stopDistance Distance to stop loss in price
     */
    double quantityForRisk(double riskPercent, double stopDistance);

    // ===== Technical Indicators =====

    double sma(int period);
    double ema(int period);
    double rsi(int period);
    double atr(int period);
    double highest(int period);
    double lowest(int period);
    double stdDev(int period);
    double macd(int fast, int slow);
    double macdSignal(int fast, int slow, int signal);
    double bollingerUpper(int period, double stdDevs);
    double bollingerLower(int period, double stdDevs);
    double momentum(int period);
    double roc(int period);  // Rate of change
    double adx(int period);  // Average Directional Index
    double cci(int period);  // Commodity Channel Index
    double williamsR(int period);
    double stochK(int period);
    double stochD(int period, int smoothing);

    // ===== Logging =====

    void log(String message);
    void logTrade(String action, double quantity, double price, String reason);
}
