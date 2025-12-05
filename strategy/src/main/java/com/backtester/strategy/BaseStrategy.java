package com.backtester.strategy;

import com.backtester.common.strategy.ExecutableStrategy;
import com.backtester.common.strategy.StrategyExecutionContext;
import com.backtester.common.model.Bar;
import com.backtester.common.model.TimeSeries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for strategies that provides common functionality.
 * Strategies have FULL control over trading - the engine does not manage orders.
 *
 * Subclasses implement:
 * - getName() - Strategy name
 * - getDescription() - Strategy description
 * - getParameters() - Configurable parameters
 * - onBar(context) - Called each bar to execute trading logic
 *
 * Trading control:
 * - Strategies decide WHEN to enter/exit
 * - Strategies decide HOW to enter/exit (market, limit, stop)
 * - Strategies implement their own stop loss / take profit logic
 * - Strategies control position sizing
 */
public abstract class BaseStrategy implements Strategy, ExecutableStrategy {

    protected StrategyExecutionContext ctx;
    protected Map<String, Object> parameterValues = new HashMap<>();

    // ===== Strategy Interface =====

    @Override
    public abstract String getName();

    @Override
    public abstract String getDescription();

    @Override
    public abstract List<StrategyParameter> getParameters();

    @Override
    public void setParameters(Map<String, Object> parameters) {
        this.parameterValues.putAll(parameters);
    }

    @Override
    public Map<String, Object> getParameterValues() {
        return new HashMap<>(parameterValues);
    }

    @Override
    public int getWarmupBars() {
        return 50; // Default warmup, override if needed
    }

    // ===== ExecutableStrategy Interface =====

    @Override
    public void initialize(StrategyExecutionContext context) {
        this.ctx = context;
        // Initialize default parameter values
        for (StrategyParameter param : getParameters()) {
            if (!parameterValues.containsKey(param.name())) {
                parameterValues.put(param.name(), param.defaultValue());
            }
        }
        onInitialize();
    }

    @Override
    public void onBar(StrategyExecutionContext context) {
        this.ctx = context;
        onBar(context.getCurrentBar());
    }

    @Override
    public void onEnd(StrategyExecutionContext context) {
        this.ctx = context;
        onEnd();
    }

    // ===== Legacy Strategy Interface =====

    @Override
    public abstract void onBar(Bar bar);

    @Override
    public void onEnd() {
        // Default: do nothing
    }

    /**
     * Override to perform custom initialization.
     */
    protected void onInitialize() {
        // Default: do nothing
    }

    // ===== Parameter Helpers =====

    protected int getIntParam(String name) {
        Object value = parameterValues.get(name);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    protected double getDoubleParam(String name) {
        Object value = parameterValues.get(name);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    protected boolean getBoolParam(String name) {
        Object value = parameterValues.get(name);
        return value instanceof Boolean ? (Boolean) value : false;
    }

    protected String getStringParam(String name) {
        Object value = parameterValues.get(name);
        return value != null ? value.toString() : "";
    }

    // ===== Market Data Helpers =====

    protected TimeSeries getData() {
        return ctx.getData();
    }

    protected Bar getCurrentBar() {
        return ctx.getCurrentBar();
    }

    protected int getBarIndex() {
        return ctx.getBarIndex();
    }

    protected Bar getBar(int index) {
        return ctx.getBar(index);
    }

    protected List<Bar> getPreviousBars(int count) {
        return ctx.getPreviousBars(count);
    }

    protected double[] getCloses(int count) {
        return ctx.getCloses(count);
    }

    protected double[][] getOHLCV(int count) {
        return ctx.getOHLCV(count);
    }

    // ===== Account State Helpers =====

    protected double getEquity() {
        return ctx.getEquity();
    }

    protected double getCash() {
        return ctx.getCash();
    }

    protected double getPositionSize() {
        return ctx.getPositionSize();
    }

    protected double getPositionEntryPrice() {
        return ctx.getPositionEntryPrice();
    }

    protected boolean hasPosition() {
        return ctx.hasPosition();
    }

    protected boolean isLong() {
        return ctx.isLong();
    }

    protected boolean isShort() {
        return ctx.isShort();
    }

    protected double getUnrealizedPnL() {
        return ctx.getUnrealizedPnL();
    }

    // ===== Order Execution (Full Strategy Control) =====

    /**
     * Execute a market order immediately.
     * @param quantity Positive = buy, Negative = sell
     * @return Fill price after slippage/spread
     */
    protected double marketOrder(double quantity) {
        return ctx.executeMarketOrder(quantity);
    }

    /**
     * Execute at a specific price (limit order simulation).
     * Only fills if price is within current bar's range.
     * @return Fill price, or NaN if not filled
     */
    protected double limitOrder(double quantity, double price) {
        return ctx.executeAtPrice(quantity, price);
    }

    /**
     * Buy at market price.
     * @param quantity Quantity to buy (positive)
     */
    protected double buy(double quantity) {
        return ctx.executeMarketOrder(Math.abs(quantity));
    }

    /**
     * Sell at market price.
     * @param quantity Quantity to sell (positive)
     */
    protected double sell(double quantity) {
        return ctx.executeMarketOrder(-Math.abs(quantity));
    }

    /**
     * Buy at limit price.
     */
    protected double buyLimit(double quantity, double price) {
        return ctx.executeAtPrice(Math.abs(quantity), price);
    }

    /**
     * Sell at limit price.
     */
    protected double sellLimit(double quantity, double price) {
        return ctx.executeAtPrice(-Math.abs(quantity), price);
    }

    /**
     * Close entire position at market.
     */
    protected double closePosition() {
        return ctx.closePosition();
    }

    /**
     * Close position at specific price.
     */
    protected double closePositionAt(double price) {
        return ctx.closePositionAtPrice(price);
    }

    // ===== Position Sizing Helpers =====

    /**
     * Calculate quantity for a given dollar amount.
     */
    protected double quantityForDollars(double dollars) {
        return ctx.quantityForDollars(dollars);
    }

    /**
     * Calculate quantity for a percentage of equity.
     */
    protected double quantityForPercent(double percent) {
        return ctx.quantityForPercentage(percent);
    }

    /**
     * Calculate quantity using risk-based sizing.
     * @param riskPercent Percent of equity to risk
     * @param stopDistance Distance to stop loss in price
     */
    protected double quantityForRisk(double riskPercent, double stopDistance) {
        return ctx.quantityForRisk(riskPercent, stopDistance);
    }

    /**
     * Full position size (100% of equity).
     */
    protected double fullPosition() {
        return quantityForPercent(100.0);
    }

    /**
     * Half position size (50% of equity).
     */
    protected double halfPosition() {
        return quantityForPercent(50.0);
    }

    // ===== Technical Indicators =====

    protected double sma(int period) {
        return ctx.sma(period);
    }

    protected double ema(int period) {
        return ctx.ema(period);
    }

    protected double rsi(int period) {
        return ctx.rsi(period);
    }

    protected double atr(int period) {
        return ctx.atr(period);
    }

    protected double highest(int period) {
        return ctx.highest(period);
    }

    protected double lowest(int period) {
        return ctx.lowest(period);
    }

    protected double stdDev(int period) {
        return ctx.stdDev(period);
    }

    protected double macd(int fast, int slow) {
        return ctx.macd(fast, slow);
    }

    protected double macdSignal(int fast, int slow, int signal) {
        return ctx.macdSignal(fast, slow, signal);
    }

    protected double bollingerUpper(int period, double stdDevs) {
        return ctx.bollingerUpper(period, stdDevs);
    }

    protected double bollingerLower(int period, double stdDevs) {
        return ctx.bollingerLower(period, stdDevs);
    }

    protected double momentum(int period) {
        return ctx.momentum(period);
    }

    protected double roc(int period) {
        return ctx.roc(period);
    }

    protected double adx(int period) {
        return ctx.adx(period);
    }

    protected double cci(int period) {
        return ctx.cci(period);
    }

    protected double williamsR(int period) {
        return ctx.williamsR(period);
    }

    protected double stochK(int period) {
        return ctx.stochK(period);
    }

    protected double stochD(int period, int smoothing) {
        return ctx.stochD(period, smoothing);
    }

    // ===== Logging =====

    protected void log(String message) {
        ctx.log(message);
    }

    protected void logTrade(String action, double quantity, double price, String reason) {
        ctx.logTrade(action, quantity, price, reason);
    }
}
