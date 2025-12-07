package com.backtester.stats;

import com.backtester.core.engine.BacktestConfig;
import com.backtester.core.engine.BacktestResult;
import com.backtester.core.engine.SimpleBacktestEngine;
import com.backtester.core.market.CommissionModel;
import com.backtester.core.market.SlippageModel;
import com.backtester.core.market.SpreadModel;
import com.backtester.common.model.Bar;
import com.backtester.common.model.TimeSeries;
import com.backtester.strategy.BaseStrategy;

import java.util.*;
import java.util.concurrent.*;

/**
 * Stress testing framework for strategy robustness analysis.
 *
 * Tests strategy under various adverse conditions:
 * - Higher transaction costs
 * - Increased volatility
 * - Market crashes
 * - Extended drawdowns
 * - Gap events
 * - Regime changes
 */
public class StressTester {

    private final BacktestConfig baseConfig;
    private final ExecutorService executor;

    public StressTester(BacktestConfig baseConfig) {
        this.baseConfig = baseConfig;
        this.executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
    }

    /**
     * Run all stress tests on a strategy.
     */
    public StressTestReport runAllTests(BaseStrategy strategy, TimeSeries data) {
        Map<String, ScenarioResult> results = new LinkedHashMap<>();

        // Baseline
        results.put("Baseline", runScenario(strategy, data, baseConfig, "Normal conditions"));

        // Higher costs
        results.put("2x Commission", runWithHigherCosts(strategy, data, 2.0, 1.0, 1.0));
        results.put("3x Commission", runWithHigherCosts(strategy, data, 3.0, 1.0, 1.0));
        results.put("2x Slippage", runWithHigherCosts(strategy, data, 1.0, 2.0, 1.0));
        results.put("2x Spread", runWithHigherCosts(strategy, data, 1.0, 1.0, 2.0));
        results.put("All Costs 2x", runWithHigherCosts(strategy, data, 2.0, 2.0, 2.0));

        // Volatility stress
        results.put("1.5x Volatility", runWithModifiedVolatility(strategy, data, 1.5));
        results.put("2x Volatility", runWithModifiedVolatility(strategy, data, 2.0));
        results.put("0.5x Volatility", runWithModifiedVolatility(strategy, data, 0.5));

        // Market crash scenarios
        results.put("10% Flash Crash", runWithCrash(strategy, data, 0.10));
        results.put("20% Market Crash", runWithCrash(strategy, data, 0.20));
        results.put("30% Bear Market", runWithCrash(strategy, data, 0.30));

        // Gap events
        results.put("Daily Gaps (+/-2%)", runWithGaps(strategy, data, 0.02));
        results.put("Weekly Gaps (+/-5%)", runWithGaps(strategy, data, 0.05));

        // Extended drawdown
        results.put("Extended Sideways", runWithSideways(strategy, data, 0.3));

        // Regime change
        results.put("Trend Reversal", runWithTrendReversal(strategy, data));

        return new StressTestReport(results, baseConfig);
    }

    /**
     * Run with multiplied transaction costs.
     */
    private ScenarioResult runWithHigherCosts(BaseStrategy strategy, TimeSeries data,
                                               double commissionMult, double slippageMult, double spreadMult) {
        BacktestConfig stressConfig = new BacktestConfig(
                baseConfig.initialCapital(),
                new CommissionModel(
                        baseConfig.commissionModel().getType(),
                        baseConfig.commissionModel().getValue() * commissionMult
                ),
                new SpreadModel(
                        baseConfig.spreadModel().getType(),
                        baseConfig.spreadModel().getValue() * spreadMult
                ),
                new SlippageModel(
                        baseConfig.slippageModel().getType(),
                        baseConfig.slippageModel().getValue() * slippageMult
                ),
                baseConfig.allowShorts(),
                baseConfig.maxPositionSizePercent(),
                baseConfig.warmupBars(),
                baseConfig.integerQuantityOnly()
        );

        String description = String.format("Commission %.1fx, Slippage %.1fx, Spread %.1fx",
                commissionMult, slippageMult, spreadMult);

        return runScenario(strategy, data, stressConfig, description);
    }

    /**
     * Run with modified volatility.
     */
    private ScenarioResult runWithModifiedVolatility(BaseStrategy strategy, TimeSeries data,
                                                      double volatilityMult) {
        TimeSeries modifiedData = modifyVolatility(data, volatilityMult);
        String description = String.format("Volatility multiplied by %.1fx", volatilityMult);
        return runScenario(strategy, modifiedData, baseConfig, description);
    }

    /**
     * Run with a sudden crash at a random point.
     */
    private ScenarioResult runWithCrash(BaseStrategy strategy, TimeSeries data, double crashPercent) {
        TimeSeries modifiedData = injectCrash(data, crashPercent);
        String description = String.format("%.0f%% crash injected mid-period", crashPercent * 100);
        return runScenario(strategy, modifiedData, baseConfig, description);
    }

    /**
     * Run with random gaps.
     */
    private ScenarioResult runWithGaps(BaseStrategy strategy, TimeSeries data, double gapSize) {
        TimeSeries modifiedData = injectGaps(data, gapSize);
        String description = String.format("+/-%.1f%% gaps injected", gapSize * 100);
        return runScenario(strategy, modifiedData, baseConfig, description);
    }

    /**
     * Run with extended sideways period.
     */
    private ScenarioResult runWithSideways(BaseStrategy strategy, TimeSeries data, double portion) {
        TimeSeries modifiedData = injectSideways(data, portion);
        String description = String.format("%.0f%% of data converted to sideways", portion * 100);
        return runScenario(strategy, modifiedData, baseConfig, description);
    }

    /**
     * Run with trend reversal.
     */
    private ScenarioResult runWithTrendReversal(BaseStrategy strategy, TimeSeries data) {
        TimeSeries modifiedData = injectTrendReversal(data);
        String description = "Major trend reversal mid-period";
        return runScenario(strategy, modifiedData, baseConfig, description);
    }

    /**
     * Run a single scenario.
     */
    private ScenarioResult runScenario(BaseStrategy strategy, TimeSeries data,
                                        BacktestConfig config, String description) {
        try {
            // Create fresh strategy instance
            BaseStrategy testStrategy = strategy.getClass().getDeclaredConstructor().newInstance();
            testStrategy.setParameters(strategy.getParameterValues());

            SimpleBacktestEngine engine = new SimpleBacktestEngine(config);
            BacktestResult result = engine.run(testStrategy, data);

            StatisticsCalculator calculator = new StatisticsCalculator();
            BacktestStatistics stats = calculator.calculate(result);

            return new ScenarioResult(
                    description,
                    result.getNetReturnPercent(),
                    stats.sharpeRatio(),
                    stats.maxDrawdownPercent(),
                    stats.winRate(),
                    stats.totalTrades(),
                    true,
                    null
            );

        } catch (Exception e) {
            return new ScenarioResult(
                    description,
                    0, 0, 0, 0, 0,
                    false,
                    e.getMessage()
            );
        }
    }

    /**
     * Modify data volatility.
     */
    private TimeSeries modifyVolatility(TimeSeries original, double mult) {
        List<Bar> newBars = new ArrayList<>();
        double basePrice = original.getFirst().close();

        for (int i = 0; i < original.size(); i++) {
            Bar bar = original.get(i);

            // Calculate returns and multiply
            double midPrice = (bar.open() + bar.close()) / 2;
            double openReturn = (bar.open() - midPrice) / midPrice * mult;
            double highReturn = (bar.high() - midPrice) / midPrice * mult;
            double lowReturn = (bar.low() - midPrice) / midPrice * mult;
            double closeReturn = (bar.close() - midPrice) / midPrice * mult;

            double newOpen = midPrice * (1 + openReturn);
            double newHigh = midPrice * (1 + highReturn);
            double newLow = midPrice * (1 + lowReturn);
            double newClose = midPrice * (1 + closeReturn);

            // Ensure high >= max(open, close) and low <= min(open, close)
            newHigh = Math.max(newHigh, Math.max(newOpen, newClose));
            newLow = Math.min(newLow, Math.min(newOpen, newClose));

            newBars.add(new Bar(bar.timestamp(), newOpen, newHigh, newLow, newClose, bar.volume(), i));
        }

        return new TimeSeries(original.getSymbol() + "_VOL" + mult, newBars);
    }

    /**
     * Inject a crash at midpoint.
     */
    private TimeSeries injectCrash(TimeSeries original, double crashPercent) {
        List<Bar> newBars = new ArrayList<>();
        int crashStart = original.size() / 2;
        int crashDuration = Math.min(50, original.size() / 10);

        double crashFactor = 1.0;
        double dailyDrop = crashPercent / crashDuration;

        for (int i = 0; i < original.size(); i++) {
            Bar bar = original.get(i);

            if (i >= crashStart && i < crashStart + crashDuration) {
                crashFactor *= (1 - dailyDrop);
            }

            double newOpen = bar.open() * crashFactor;
            double newHigh = bar.high() * crashFactor;
            double newLow = bar.low() * crashFactor;
            double newClose = bar.close() * crashFactor;

            newBars.add(new Bar(bar.timestamp(), newOpen, newHigh, newLow, newClose, bar.volume(), i));
        }

        return new TimeSeries(original.getSymbol() + "_CRASH", newBars);
    }

    /**
     * Inject random gaps.
     */
    private TimeSeries injectGaps(TimeSeries original, double maxGap) {
        List<Bar> newBars = new ArrayList<>();
        Random random = new Random(42);
        double gapFactor = 1.0;

        for (int i = 0; i < original.size(); i++) {
            Bar bar = original.get(i);

            // Random gap every ~50 bars
            if (i > 0 && random.nextInt(50) == 0) {
                double gap = (random.nextDouble() * 2 - 1) * maxGap;
                gapFactor *= (1 + gap);
            }

            double newOpen = bar.open() * gapFactor;
            double newHigh = bar.high() * gapFactor;
            double newLow = bar.low() * gapFactor;
            double newClose = bar.close() * gapFactor;

            newBars.add(new Bar(bar.timestamp(), newOpen, newHigh, newLow, newClose, bar.volume(), i));
        }

        return new TimeSeries(original.getSymbol() + "_GAPS", newBars);
    }

    /**
     * Inject sideways period.
     */
    private TimeSeries injectSideways(TimeSeries original, double portion) {
        List<Bar> newBars = new ArrayList<>();
        int sidewaysStart = original.size() / 3;
        int sidewaysEnd = sidewaysStart + (int) (original.size() * portion);
        double flatPrice = original.get(sidewaysStart).close();

        for (int i = 0; i < original.size(); i++) {
            Bar bar = original.get(i);

            if (i >= sidewaysStart && i < sidewaysEnd) {
                // Reduce range to create sideways movement
                double range = (bar.high() - bar.low()) * 0.3;
                double newHigh = flatPrice + range / 2;
                double newLow = flatPrice - range / 2;
                newBars.add(new Bar(bar.timestamp(), flatPrice, newHigh, newLow, flatPrice, bar.volume(), i));
            } else {
                newBars.add(bar);
            }
        }

        return new TimeSeries(original.getSymbol() + "_SIDEWAYS", newBars);
    }

    /**
     * Inject trend reversal.
     */
    private TimeSeries injectTrendReversal(TimeSeries original) {
        List<Bar> newBars = new ArrayList<>();
        int reversalPoint = original.size() / 2;

        // Calculate cumulative return up to reversal
        double totalReturn = original.get(reversalPoint).close() / original.getFirst().close() - 1;

        for (int i = 0; i < original.size(); i++) {
            Bar bar = original.get(i);

            if (i > reversalPoint) {
                // Reverse the returns after midpoint
                int mirrorIdx = reversalPoint - (i - reversalPoint);
                if (mirrorIdx >= 0) {
                    Bar mirrorBar = original.get(mirrorIdx);
                    double basePrice = original.get(reversalPoint).close();
                    double mirrorReturn = mirrorBar.close() / basePrice;

                    double newOpen = bar.open() / mirrorReturn;
                    double newHigh = bar.high() / mirrorReturn;
                    double newLow = bar.low() / mirrorReturn;
                    double newClose = bar.close() / mirrorReturn;

                    newBars.add(new Bar(bar.timestamp(), newOpen, newHigh, newLow, newClose, bar.volume(), i));
                    continue;
                }
            }
            newBars.add(bar);
        }

        return new TimeSeries(original.getSymbol() + "_REVERSAL", newBars);
    }

    /**
     * Shutdown executor.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Result of a single stress scenario.
     */
    public record ScenarioResult(
            String description,
            double netReturn,
            double sharpeRatio,
            double maxDrawdown,
            double winRate,
            int trades,
            boolean success,
            String error
    ) {
        public boolean isProfitable() {
            return netReturn > 0;
        }

        public String getStatus() {
            if (!success) return "FAILED";
            if (netReturn > 0 && maxDrawdown < 30) return "PASS";
            if (netReturn > 0) return "MARGINAL";
            return "FAIL";
        }
    }

    /**
     * Complete stress test report.
     */
    public record StressTestReport(
            Map<String, ScenarioResult> scenarios,
            BacktestConfig baseConfig
    ) {
        public int getPassCount() {
            return (int) scenarios.values().stream()
                    .filter(r -> r.getStatus().equals("PASS"))
                    .count();
        }

        public int getFailCount() {
            return (int) scenarios.values().stream()
                    .filter(r -> r.getStatus().equals("FAIL") || r.getStatus().equals("FAILED"))
                    .count();
        }

        public double getAverageReturn() {
            return scenarios.values().stream()
                    .filter(ScenarioResult::success)
                    .mapToDouble(ScenarioResult::netReturn)
                    .average()
                    .orElse(0);
        }

        public double getWorstReturn() {
            return scenarios.values().stream()
                    .filter(ScenarioResult::success)
                    .mapToDouble(ScenarioResult::netReturn)
                    .min()
                    .orElse(0);
        }

        public String getRobustnessRating() {
            double passRate = (double) getPassCount() / scenarios.size();
            if (passRate > 0.8) return "Excellent";
            if (passRate > 0.6) return "Good";
            if (passRate > 0.4) return "Moderate";
            return "Poor";
        }

        public ScenarioResult getBaseline() {
            return scenarios.get("Baseline");
        }
    }
}
