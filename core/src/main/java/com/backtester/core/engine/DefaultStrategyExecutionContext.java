package com.backtester.core.engine;

import com.backtester.core.portfolio.Portfolio;
import com.backtester.core.portfolio.Position;
import com.backtester.core.portfolio.Side;
import com.backtester.common.model.Bar;
import com.backtester.common.model.TimeSeries;
import com.backtester.common.strategy.StrategyExecutionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of StrategyExecutionContext.
 * Gives strategies FULL control over trading - no order queue.
 * Executions happen immediately when strategy calls execute methods.
 */
public class DefaultStrategyExecutionContext implements StrategyExecutionContext {

    private final TimeSeries data;
    private final Portfolio portfolio;
    private final BacktestConfig config;
    private final IndicatorCalculator indicators;

    private int currentBarIndex;
    private double totalCommissions;
    private double totalSlippage;
    private double totalSpreadCost;

    public DefaultStrategyExecutionContext(TimeSeries data, Portfolio portfolio, BacktestConfig config) {
        this.data = data;
        this.portfolio = portfolio;
        this.config = config;
        this.indicators = new IndicatorCalculator(data);
        this.currentBarIndex = 0;
    }

    /**
     * Set current bar index - called by engine during simulation.
     */
    public void setCurrentBarIndex(int index) {
        this.currentBarIndex = index;
    }

    /**
     * Update portfolio with current bar prices - called by engine.
     */
    public void updatePortfolio(Bar bar) {
        portfolio.update(bar);
    }

    // ===== Market Data =====

    @Override
    public TimeSeries getData() {
        return data;
    }

    @Override
    public Bar getCurrentBar() {
        return data.get(currentBarIndex);
    }

    @Override
    public int getBarIndex() {
        return currentBarIndex;
    }

    @Override
    public Bar getBar(int index) {
        if (index < 0 || index >= data.size()) return null;
        return data.get(index);
    }

    @Override
    public List<Bar> getPreviousBars(int count) {
        List<Bar> bars = new ArrayList<>();
        int start = Math.max(0, currentBarIndex - count + 1);
        for (int i = start; i <= currentBarIndex; i++) {
            bars.add(data.get(i));
        }
        return bars;
    }

    @Override
    public double[] getCloses(int count) {
        int start = Math.max(0, currentBarIndex - count + 1);
        int actualCount = currentBarIndex - start + 1;
        double[] closes = new double[actualCount];
        for (int i = 0; i < actualCount; i++) {
            closes[i] = data.get(start + i).close();
        }
        return closes;
    }

    @Override
    public double[][] getOHLCV(int count) {
        int start = Math.max(0, currentBarIndex - count + 1);
        int actualCount = currentBarIndex - start + 1;
        double[][] ohlcv = new double[actualCount][5];
        for (int i = 0; i < actualCount; i++) {
            Bar bar = data.get(start + i);
            ohlcv[i][0] = bar.open();
            ohlcv[i][1] = bar.high();
            ohlcv[i][2] = bar.low();
            ohlcv[i][3] = bar.close();
            ohlcv[i][4] = bar.volume();
        }
        return ohlcv;
    }

    // ===== Account State =====

    @Override
    public double getEquity() {
        return portfolio.getEquity();
    }

    @Override
    public double getCash() {
        return portfolio.getCash();
    }

    @Override
    public double getPositionSize() {
        Position pos = portfolio.getPosition();
        if (pos == null) return 0;
        return pos.getSide() == Side.LONG ? pos.getQuantity() : -pos.getQuantity();
    }

    @Override
    public double getPositionEntryPrice() {
        Position pos = portfolio.getPosition();
        return pos != null ? pos.getEntryPrice() : 0;
    }

    @Override
    public boolean hasPosition() {
        return portfolio.hasPosition();
    }

    @Override
    public boolean isLong() {
        Position pos = portfolio.getPosition();
        return pos != null && pos.isLong();
    }

    @Override
    public boolean isShort() {
        Position pos = portfolio.getPosition();
        return pos != null && pos.isShort();
    }

    @Override
    public double getUnrealizedPnL() {
        Position pos = portfolio.getPosition();
        if (pos == null) return 0;
        return pos.getUnrealizedPnl();
    }

    // ===== Order Execution (Strategy Controls Everything) =====

    @Override
    public double executeMarketOrder(double quantity) {
        if (quantity == 0) return Double.NaN;

        Bar bar = getCurrentBar();
        double basePrice = bar.close();

        // Apply spread
        double halfSpread = config.spreadModel().calculateHalfSpread(basePrice, bar);
        double priceAfterSpread = quantity > 0
                ? basePrice + halfSpread  // Buying at ask
                : basePrice - halfSpread; // Selling at bid

        // Apply slippage
        boolean isBuy = quantity > 0;
        double slippage = config.slippageModel().calculate(priceAfterSpread, Math.abs(quantity), bar, isBuy);
        double fillPrice = quantity > 0
                ? priceAfterSpread * (1 + slippage)
                : priceAfterSpread * (1 - slippage);

        // Calculate commission
        double commission = config.commissionModel().calculate(Math.abs(quantity), fillPrice);

        // Execute the trade
        executeTrade(quantity, fillPrice, commission, slippage * Math.abs(quantity) * priceAfterSpread, halfSpread * Math.abs(quantity));

        return fillPrice;
    }

    @Override
    public double executeAtPrice(double quantity, double price) {
        if (quantity == 0) return Double.NaN;

        Bar bar = getCurrentBar();

        // Check if price is within bar's range
        boolean canFill = (quantity > 0)
                ? price >= bar.low() && price <= bar.high()   // Buy limit: price must be reachable
                : price >= bar.low() && price <= bar.high();  // Sell limit: price must be reachable

        if (!canFill) {
            return Double.NaN; // Order not filled
        }

        // Apply commission only (no slippage for limit orders)
        double commission = config.commissionModel().calculate(Math.abs(quantity), price);

        // Execute at the specified price
        executeTrade(quantity, price, commission, 0, 0);

        return price;
    }

    @Override
    public double closePosition() {
        if (!hasPosition()) return Double.NaN;

        Position pos = portfolio.getPosition();
        double quantity = pos.getSide() == Side.LONG ? -pos.getQuantity() : pos.getQuantity();

        return executeMarketOrder(quantity);
    }

    @Override
    public double closePositionAtPrice(double price) {
        if (!hasPosition()) return Double.NaN;

        Position pos = portfolio.getPosition();
        double quantity = pos.getSide() == Side.LONG ? -pos.getQuantity() : pos.getQuantity();

        return executeAtPrice(quantity, price);
    }

    /**
     * Execute a trade - handles opening/closing positions.
     */
    private void executeTrade(double signedQuantity, double price, double commission, double slippageCost, double spreadCost) {
        Bar bar = getCurrentBar();
        double absQuantity = Math.abs(signedQuantity);
        boolean isBuy = signedQuantity > 0;

        // Track costs
        totalCommissions += commission;
        totalSlippage += slippageCost;
        totalSpreadCost += spreadCost;

        if (!portfolio.hasPosition()) {
            // Open new position
            Side side = isBuy ? Side.LONG : Side.SHORT;
            portfolio.openPosition(
                    data.getSymbol(),
                    side,
                    price,
                    absQuantity,
                    bar.timestamp(),
                    currentBarIndex,
                    commission,
                    0 // Slippage already in price
            );
        } else {
            Position pos = portfolio.getPosition();
            boolean isClosing = (pos.isLong() && !isBuy) || (pos.isShort() && isBuy);

            if (isClosing) {
                // Close position
                portfolio.closePosition(price, bar.timestamp(), currentBarIndex, commission, 0);

                // If quantity exceeds position, open reverse
                double excess = absQuantity - pos.getQuantity();
                if (excess > 0) {
                    Side newSide = isBuy ? Side.LONG : Side.SHORT;
                    double newCommission = config.commissionModel().calculate(excess, price);
                    portfolio.openPosition(
                            data.getSymbol(),
                            newSide,
                            price,
                            excess,
                            bar.timestamp(),
                            currentBarIndex,
                            newCommission,
                            0
                    );
                    totalCommissions += newCommission;
                }
            } else {
                // Adding to position - close and reopen with larger size
                double newQuantity = pos.getQuantity() + absQuantity;
                double avgPrice = (pos.getEntryPrice() * pos.getQuantity() + price * absQuantity) / newQuantity;

                // This is a simplified approach - close and reopen
                portfolio.closePosition(pos.getEntryPrice(), bar.timestamp(), currentBarIndex, 0, 0);
                portfolio.openPosition(
                        data.getSymbol(),
                        pos.getSide(),
                        avgPrice,
                        newQuantity,
                        bar.timestamp(),
                        currentBarIndex,
                        commission,
                        0
                );
            }
        }
    }

    // ===== Position Sizing Helpers =====

    @Override
    public double quantityForDollars(double dollars) {
        Bar bar = getCurrentBar();
        double price = bar.close();

        // Account for spread (buying at ask price)
        double halfSpread = config.spreadModel().calculateHalfSpread(price, bar);
        double askPrice = price + halfSpread;

        // Account for expected slippage
        double slippage = config.slippageModel().calculate(askPrice, 1.0, bar, true);
        double effectivePrice = askPrice * (1 + slippage);

        // Account for commission (rough estimate per share)
        double commissionPerShare = config.commissionModel().calculate(1.0, effectivePrice);

        // Total cost per share
        double costPerShare = effectivePrice + commissionPerShare;

        // Calculate quantity that fits within budget
        return Math.floor(dollars / costPerShare);
    }

    @Override
    public double quantityForPercentage(double percent) {
        // Use available cash, not equity, to avoid over-leveraging
        double availableCash = getCash();
        double targetAmount = availableCash * percent / 100.0;
        return quantityForDollars(targetAmount);
    }

    @Override
    public double quantityForRisk(double riskPercent, double stopDistance) {
        if (stopDistance <= 0) return 0;
        double riskAmount = getEquity() * riskPercent / 100.0;
        double quantity = riskAmount / stopDistance;

        // Ensure we can actually afford this quantity
        Bar bar = getCurrentBar();
        double price = bar.close();
        double halfSpread = config.spreadModel().calculateHalfSpread(price, bar);
        double askPrice = price + halfSpread;
        double maxAffordable = getCash() / askPrice;

        return Math.min(quantity, Math.floor(maxAffordable * 0.99)); // 1% buffer
    }

    // ===== Technical Indicators =====

    @Override
    public double sma(int period) {
        return indicators.sma(currentBarIndex, period);
    }

    @Override
    public double ema(int period) {
        return indicators.ema(currentBarIndex, period);
    }

    @Override
    public double rsi(int period) {
        return indicators.rsi(currentBarIndex, period);
    }

    @Override
    public double atr(int period) {
        return indicators.atr(currentBarIndex, period);
    }

    @Override
    public double highest(int period) {
        return indicators.highest(currentBarIndex, period);
    }

    @Override
    public double lowest(int period) {
        return indicators.lowest(currentBarIndex, period);
    }

    @Override
    public double stdDev(int period) {
        return indicators.stdDev(currentBarIndex, period);
    }

    @Override
    public double macd(int fast, int slow) {
        return indicators.macd(currentBarIndex, fast, slow);
    }

    @Override
    public double macdSignal(int fast, int slow, int signal) {
        return indicators.macdSignal(currentBarIndex, fast, slow, signal);
    }

    @Override
    public double bollingerUpper(int period, double stdDevs) {
        return sma(period) + stdDevs * stdDev(period);
    }

    @Override
    public double bollingerLower(int period, double stdDevs) {
        return sma(period) - stdDevs * stdDev(period);
    }

    @Override
    public double momentum(int period) {
        return indicators.momentum(currentBarIndex, period);
    }

    @Override
    public double roc(int period) {
        return indicators.roc(currentBarIndex, period);
    }

    @Override
    public double adx(int period) {
        return indicators.adx(currentBarIndex, period);
    }

    @Override
    public double cci(int period) {
        return indicators.cci(currentBarIndex, period);
    }

    @Override
    public double williamsR(int period) {
        return indicators.williamsR(currentBarIndex, period);
    }

    @Override
    public double stochK(int period) {
        return indicators.stochK(currentBarIndex, period);
    }

    @Override
    public double stochD(int period, int smoothing) {
        return indicators.stochD(currentBarIndex, period, smoothing);
    }

    // ===== Logging =====

    @Override
    public void log(String message) {
        Bar bar = getCurrentBar();
        System.out.println("[" + bar.getDateTime() + "] " + message);
    }

    @Override
    public void logTrade(String action, double quantity, double price, String reason) {
        Bar bar = getCurrentBar();
        System.out.printf("[%s] %s %.4f @ %.5f (%s)%n",
                bar.getDateTime(), action, quantity, price, reason);
    }

    // ===== Statistics Getters =====

    public double getTotalCommissions() {
        return totalCommissions;
    }

    public double getTotalSlippage() {
        return totalSlippage;
    }

    public double getTotalSpreadCost() {
        return totalSpreadCost;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    /**
     * Indicator calculator with caching.
     */
    private static class IndicatorCalculator {
        private final TimeSeries data;
        private final double[] closes;
        private final double[] highs;
        private final double[] lows;

        public IndicatorCalculator(TimeSeries data) {
            this.data = data;
            this.closes = data.getCloses();
            this.highs = data.getHighs();
            this.lows = data.getLows();
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
            double ema = sma(period - 1, period);
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

        public double highest(int barIndex, int period) {
            if (barIndex < period - 1) return Double.NaN;
            double max = Double.MIN_VALUE;
            for (int i = barIndex - period + 1; i <= barIndex; i++) {
                max = Math.max(max, highs[i]);
            }
            return max;
        }

        public double lowest(int barIndex, int period) {
            if (barIndex < period - 1) return Double.NaN;
            double min = Double.MAX_VALUE;
            for (int i = barIndex - period + 1; i <= barIndex; i++) {
                min = Math.min(min, lows[i]);
            }
            return min;
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

        public double macd(int barIndex, int fast, int slow) {
            double fastEma = ema(barIndex, fast);
            double slowEma = ema(barIndex, slow);
            if (Double.isNaN(fastEma) || Double.isNaN(slowEma)) return Double.NaN;
            return fastEma - slowEma;
        }

        public double macdSignal(int barIndex, int fast, int slow, int signal) {
            if (barIndex < slow + signal - 1) return Double.NaN;

            double multiplier = 2.0 / (signal + 1);
            double signalEma = macd(slow + signal - 2, fast, slow);

            for (int i = slow + signal - 1; i <= barIndex; i++) {
                double macdVal = macd(i, fast, slow);
                signalEma = (macdVal - signalEma) * multiplier + signalEma;
            }
            return signalEma;
        }

        public double momentum(int barIndex, int period) {
            if (barIndex < period) return Double.NaN;
            return closes[barIndex] - closes[barIndex - period];
        }

        public double roc(int barIndex, int period) {
            if (barIndex < period) return Double.NaN;
            double prev = closes[barIndex - period];
            if (prev == 0) return Double.NaN;
            return ((closes[barIndex] - prev) / prev) * 100;
        }

        public double adx(int barIndex, int period) {
            if (barIndex < period * 2) return Double.NaN;

            double sumDX = 0;
            for (int i = barIndex - period + 1; i <= barIndex; i++) {
                double plusDM = highs[i] - highs[i - 1];
                double minusDM = lows[i - 1] - lows[i];

                if (plusDM < 0) plusDM = 0;
                if (minusDM < 0) minusDM = 0;
                if (plusDM > minusDM) minusDM = 0;
                else plusDM = 0;

                double tr = Math.max(highs[i] - lows[i],
                        Math.max(Math.abs(highs[i] - closes[i - 1]),
                                Math.abs(lows[i] - closes[i - 1])));

                double plusDI = tr > 0 ? (plusDM / tr) * 100 : 0;
                double minusDI = tr > 0 ? (minusDM / tr) * 100 : 0;

                double diSum = plusDI + minusDI;
                double dx = diSum > 0 ? Math.abs(plusDI - minusDI) / diSum * 100 : 0;
                sumDX += dx;
            }

            return sumDX / period;
        }

        public double cci(int barIndex, int period) {
            if (barIndex < period - 1) return Double.NaN;

            // Calculate typical price and its SMA
            double tpSum = 0;
            for (int i = barIndex - period + 1; i <= barIndex; i++) {
                double tp = (highs[i] + lows[i] + closes[i]) / 3;
                tpSum += tp;
            }
            double tpSma = tpSum / period;

            // Calculate mean deviation
            double mdSum = 0;
            for (int i = barIndex - period + 1; i <= barIndex; i++) {
                double tp = (highs[i] + lows[i] + closes[i]) / 3;
                mdSum += Math.abs(tp - tpSma);
            }
            double meanDev = mdSum / period;

            if (meanDev == 0) return 0;

            double tp = (highs[barIndex] + lows[barIndex] + closes[barIndex]) / 3;
            return (tp - tpSma) / (0.015 * meanDev);
        }

        public double williamsR(int barIndex, int period) {
            if (barIndex < period - 1) return Double.NaN;

            double highest = highest(barIndex, period);
            double lowest = lowest(barIndex, period);

            if (highest == lowest) return -50;

            return ((highest - closes[barIndex]) / (highest - lowest)) * -100;
        }

        public double stochK(int barIndex, int period) {
            if (barIndex < period - 1) return Double.NaN;

            double highest = highest(barIndex, period);
            double lowest = lowest(barIndex, period);

            if (highest == lowest) return 50;

            return ((closes[barIndex] - lowest) / (highest - lowest)) * 100;
        }

        public double stochD(int barIndex, int period, int smoothing) {
            if (barIndex < period + smoothing - 2) return Double.NaN;

            double sum = 0;
            for (int i = barIndex - smoothing + 1; i <= barIndex; i++) {
                sum += stochK(i, period);
            }
            return sum / smoothing;
        }
    }
}
