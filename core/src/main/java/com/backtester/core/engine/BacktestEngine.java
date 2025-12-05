package com.backtester.core.engine;

import com.backtester.core.market.CommissionModel;
import com.backtester.core.market.MarketSimulator;
import com.backtester.core.market.SlippageModel;
import com.backtester.core.market.SpreadModel;
import com.backtester.core.order.*;
import com.backtester.core.portfolio.Portfolio;
import com.backtester.core.portfolio.Position;
import com.backtester.core.portfolio.Side;
import com.backtester.core.portfolio.Trade;
import com.backtester.common.model.Bar;
import com.backtester.data.model.DataInfo;
import com.backtester.common.model.TimeSeries;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Main backtesting engine that orchestrates the simulation.
 */
public class BacktestEngine {
    private final BacktestConfig config;
    private final MarketSimulator marketSimulator;

    public BacktestEngine(BacktestConfig config) {
        this.config = config;
        this.marketSimulator = new MarketSimulator(
                config.commissionModel(),
                config.spreadModel(),
                config.slippageModel()
        );
    }

    /**
     * Run a backtest with the given strategy and data.
     */
    public BacktestResult run(StrategyAdapter strategy, TimeSeries data) {
        long startTime = System.currentTimeMillis();

        // Initialize components
        Portfolio portfolio = new Portfolio(config.initialCapital());
        OrderManager orderManager = new OrderManager();

        // Create context for the strategy
        DefaultStrategyContext context = new DefaultStrategyContext(
                data, portfolio, orderManager, config, marketSimulator
        );

        // Initialize strategy
        strategy.initialize(context);

        int warmupBars = Math.max(config.warmupBars(), strategy.getWarmupBars());

        // Main simulation loop
        for (int i = 0; i < data.size(); i++) {
            Bar bar = data.get(i);
            context.setCurrentBarIndex(i);

            // Process pending orders from previous bar
            List<Fill> fills = orderManager.processOrders(bar,
                    config.commissionModel().calculate(1, bar.close()),
                    config.slippageModel().getValue() / 100.0);

            // Apply fills to portfolio
            for (Fill fill : fills) {
                applyFill(portfolio, fill, bar);
            }

            // Update portfolio with current prices
            portfolio.update(bar);

            // Skip strategy execution during warmup
            if (i >= warmupBars) {
                // Execute strategy
                strategy.onBar(bar);
            }
        }

        // Close any remaining position
        if (portfolio.hasPosition()) {
            Bar lastBar = data.getLast();
            double commission = config.commissionModel().calculate(
                    portfolio.getPosition().getQuantity(),
                    lastBar.close()
            );
            portfolio.closePosition(lastBar.close(), lastBar.timestamp(), lastBar.index(),
                    commission, config.slippageModel().getValue() / 100.0);
        }

        // Notify strategy of end
        strategy.onEnd();

        long executionTime = System.currentTimeMillis() - startTime;

        // Calculate Buy & Hold return
        double buyAndHoldReturn = calculateBuyAndHoldReturn(data);
        double buyAndHoldFinalEquity = config.initialCapital() * (1 + buyAndHoldReturn / 100.0);

        // Build result
        return BacktestResult.builder()
                .strategyName(strategy.getName())
                .config(config)
                .dataInfo(DataInfo.from(data, null))
                .runTime(LocalDateTime.now())
                .initialEquity(config.initialCapital())
                .finalEquity(portfolio.getEquity())
                .trades(portfolio.getTrades())
                .equityHistory(portfolio.getEquityHistory())
                .buyAndHoldReturn(buyAndHoldReturn)
                .buyAndHoldFinalEquity(buyAndHoldFinalEquity)
                .totalCommissions(marketSimulator.getTotalCommissions())
                .totalSlippage(marketSimulator.getTotalSlippage())
                .totalSpreadCost(marketSimulator.getTotalSpreadCost())
                .totalBars(data.size())
                .barsInMarket(portfolio.getTotalBarsInMarket())
                .executionTimeMs(executionTime)
                .build();
    }

    /**
     * Apply a fill to the portfolio.
     */
    private void applyFill(Portfolio portfolio, Fill fill, Bar bar) {
        if (fill.action() == OrderAction.BUY) {
            if (!portfolio.hasPosition()) {
                // Open long position
                portfolio.openPosition(
                        fill.symbol(),
                        Side.LONG,
                        fill.price(),
                        fill.quantity(),
                        fill.timestamp(),
                        fill.barIndex(),
                        fill.commission(),
                        0 // Slippage already applied in fill price
                );
            } else if (portfolio.getPosition().isShort()) {
                // Close short position
                portfolio.closePosition(fill.price(), fill.timestamp(), fill.barIndex(),
                        fill.commission(), 0);
            }
        } else { // SELL
            if (!portfolio.hasPosition()) {
                // Open short position
                if (config.allowShorts()) {
                    portfolio.openPosition(
                            fill.symbol(),
                            Side.SHORT,
                            fill.price(),
                            fill.quantity(),
                            fill.timestamp(),
                            fill.barIndex(),
                            fill.commission(),
                            0
                    );
                }
            } else if (portfolio.getPosition().isLong()) {
                // Close long position
                portfolio.closePosition(fill.price(), fill.timestamp(), fill.barIndex(),
                        fill.commission(), 0);
            }
        }
    }

    /**
     * Calculate Buy & Hold return percentage.
     */
    private double calculateBuyAndHoldReturn(TimeSeries data) {
        if (data.isEmpty()) return 0;
        double startPrice = data.getFirst().close();
        double endPrice = data.getLast().close();
        return ((endPrice - startPrice) / startPrice) * 100;
    }

    /**
     * Interface for strategy adapter to break circular dependency.
     */
    public interface StrategyAdapter {
        String getName();
        int getWarmupBars();
        void initialize(DefaultStrategyContext context);
        void onBar(Bar bar);
        void onEnd();
    }

    /**
     * Default implementation of StrategyContext.
     */
    public static class DefaultStrategyContext {
        private final TimeSeries data;
        private final Portfolio portfolio;
        private final OrderManager orderManager;
        private final BacktestConfig config;
        private final MarketSimulator marketSimulator;
        private int currentBarIndex;

        // Indicator caches
        private final IndicatorCache indicatorCache;

        public DefaultStrategyContext(TimeSeries data, Portfolio portfolio,
                                      OrderManager orderManager, BacktestConfig config,
                                      MarketSimulator marketSimulator) {
            this.data = data;
            this.portfolio = portfolio;
            this.orderManager = orderManager;
            this.config = config;
            this.marketSimulator = marketSimulator;
            this.indicatorCache = new IndicatorCache(data);
        }

        void setCurrentBarIndex(int index) {
            this.currentBarIndex = index;
        }

        // Market Data
        public TimeSeries getData() {
            return data;
        }

        public Bar getCurrentBar() {
            return data.get(currentBarIndex);
        }

        public int getBarIndex() {
            return currentBarIndex;
        }

        public Bar getBar(int index) {
            if (index < 0 || index >= data.size()) return null;
            return data.get(index);
        }

        // Portfolio
        public double getEquity() {
            return portfolio.getEquity();
        }

        public double getCash() {
            return portfolio.getCash();
        }

        public Position getPosition() {
            return portfolio.getPosition();
        }

        public boolean hasPosition() {
            return portfolio.hasPosition();
        }

        // Trading
        public void buy(double quantity) {
            Bar bar = getCurrentBar();
            Order order = Order.marketBuy(data.getSymbol(), quantity, bar.timestamp(), currentBarIndex);
            orderManager.submit(order);
        }

        public void sell(double quantity) {
            Bar bar = getCurrentBar();
            Order order = Order.marketSell(data.getSymbol(), quantity, bar.timestamp(), currentBarIndex);
            orderManager.submit(order);
        }

        public void buyAtLimit(double quantity, double price) {
            Bar bar = getCurrentBar();
            Order order = Order.limitBuy(data.getSymbol(), quantity, price, bar.timestamp(), currentBarIndex);
            orderManager.submit(order);
        }

        public void sellAtLimit(double quantity, double price) {
            Bar bar = getCurrentBar();
            Order order = Order.limitSell(data.getSymbol(), quantity, price, bar.timestamp(), currentBarIndex);
            orderManager.submit(order);
        }

        public void buyAtStop(double quantity, double stopPrice) {
            Bar bar = getCurrentBar();
            Order order = Order.stopBuy(data.getSymbol(), quantity, stopPrice, bar.timestamp(), currentBarIndex);
            orderManager.submit(order);
        }

        public void sellAtStop(double quantity, double stopPrice) {
            Bar bar = getCurrentBar();
            Order order = Order.stopSell(data.getSymbol(), quantity, stopPrice, bar.timestamp(), currentBarIndex);
            orderManager.submit(order);
        }

        public void closePosition() {
            if (portfolio.hasPosition()) {
                Position pos = portfolio.getPosition();
                if (pos.isLong()) {
                    sell(pos.getQuantity());
                } else {
                    buy(pos.getQuantity());
                }
            }
        }

        public void cancelAllOrders() {
            orderManager.cancelAllPending();
        }

        // Position Sizing
        public double calculatePositionSize(double percentOfEquity) {
            double equity = getEquity();
            double price = getCurrentBar().close();
            return (equity * percentOfEquity / 100.0) / price;
        }

        // Indicators
        public double sma(int period) {
            return indicatorCache.sma(currentBarIndex, period);
        }

        public double ema(int period) {
            return indicatorCache.ema(currentBarIndex, period);
        }

        public double rsi(int period) {
            return indicatorCache.rsi(currentBarIndex, period);
        }

        public double atr(int period) {
            return indicatorCache.atr(currentBarIndex, period);
        }

        public double highest(int period) {
            return data.getHighestHigh(period, currentBarIndex);
        }

        public double lowest(int period) {
            return data.getLowestLow(period, currentBarIndex);
        }

        public double stdDev(int period) {
            return indicatorCache.stdDev(currentBarIndex, period);
        }

        public void log(String message) {
            System.out.println("[" + getCurrentBar().getDateTime() + "] " + message);
        }
    }

    /**
     * Cache for indicator calculations to avoid recalculating.
     */
    private static class IndicatorCache {
        private final TimeSeries data;
        private final double[] closes;

        public IndicatorCache(TimeSeries data) {
            this.data = data;
            this.closes = data.getCloses();
        }

        public double sma(int barIndex, int period) {
            if (barIndex < period - 1) return Double.NaN;
            double sum = 0;
            for (int i = barIndex - period + 1; i <= barIndex; i++) {
                sum += closes[i];
            }
            return sum / period;
        }

        public double ema(int barIndex, int period) {
            if (barIndex < period - 1) return Double.NaN;
            double multiplier = 2.0 / (period + 1);
            double ema = sma(period - 1, period); // Start with SMA
            for (int i = period; i <= barIndex; i++) {
                ema = (closes[i] - ema) * multiplier + ema;
            }
            return ema;
        }

        public double rsi(int barIndex, int period) {
            if (barIndex < period) return Double.NaN;

            double gainSum = 0, lossSum = 0;
            for (int i = barIndex - period + 1; i <= barIndex; i++) {
                double change = closes[i] - closes[i - 1];
                if (change > 0) gainSum += change;
                else lossSum -= change;
            }

            double avgGain = gainSum / period;
            double avgLoss = lossSum / period;

            if (avgLoss == 0) return 100;
            double rs = avgGain / avgLoss;
            return 100 - (100 / (1 + rs));
        }

        public double atr(int barIndex, int period) {
            if (barIndex < period) return Double.NaN;

            double atrSum = 0;
            for (int i = barIndex - period + 1; i <= barIndex; i++) {
                Bar bar = data.get(i);
                double prevClose = i > 0 ? closes[i - 1] : bar.open();
                double tr = bar.trueRange(prevClose);
                atrSum += tr;
            }
            return atrSum / period;
        }

        public double stdDev(int barIndex, int period) {
            if (barIndex < period - 1) return Double.NaN;

            double mean = sma(barIndex, period);
            double sumSquares = 0;
            for (int i = barIndex - period + 1; i <= barIndex; i++) {
                double diff = closes[i] - mean;
                sumSquares += diff * diff;
            }
            return Math.sqrt(sumSquares / period);
        }
    }
}
