package com.backtester.stats;

import com.backtester.core.engine.BacktestConfig;
import com.backtester.core.engine.BacktestResult;
import com.backtester.core.engine.SimpleBacktestEngine;
import com.backtester.common.model.TimeSeries;
import com.backtester.strategy.BaseStrategy;
import com.backtester.strategy.StrategyParameter;

import java.util.*;
import java.util.concurrent.*;

/**
 * Walk-Forward Analysis for strategy validation.
 *
 * Splits data into rolling train/test windows:
 * |--Train--|--Test--|--Train--|--Test--|--Train--|--Test--|
 *
 * For each window:
 * 1. Optimize parameters on training data
 * 2. Test with optimal parameters on out-of-sample data
 * 3. Stitch together OOS results for realistic performance estimate
 */
public class WalkForwardAnalyzer {

    private final BacktestConfig baseConfig;
    private final int trainBars;
    private final int testBars;
    private final int stepBars;
    private final int optimizationIterations;

    /**
     * Create a walk-forward analyzer.
     *
     * @param baseConfig Base backtest configuration
     * @param trainBars Number of bars for training window
     * @param testBars Number of bars for test window
     * @param stepBars How many bars to step forward each iteration
     * @param optimizationIterations Number of parameter combinations to try
     */
    public WalkForwardAnalyzer(BacktestConfig baseConfig, int trainBars, int testBars,
                                int stepBars, int optimizationIterations) {
        this.baseConfig = baseConfig;
        this.trainBars = trainBars;
        this.testBars = testBars;
        this.stepBars = stepBars;
        this.optimizationIterations = optimizationIterations;
    }

    /**
     * Create with default settings.
     */
    public WalkForwardAnalyzer(BacktestConfig baseConfig) {
        this(baseConfig, 5000, 1000, 500, 50);
    }

    /**
     * Run walk-forward analysis on a strategy.
     */
    public WalkForwardResult analyze(BaseStrategy strategy, TimeSeries fullData) {
        List<WindowResult> windowResults = new ArrayList<>();
        List<BacktestResult> oosResults = new ArrayList<>();

        int dataSize = fullData.size();
        int windowStart = 0;

        // Calculate number of windows
        int numWindows = 0;
        int tempStart = 0;
        while (tempStart + trainBars + testBars <= dataSize) {
            numWindows++;
            tempStart += stepBars;
        }

        ExecutorService executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );

        try {
            // Process each window
            while (windowStart + trainBars + testBars <= dataSize) {
                int trainStart = windowStart;
                int trainEnd = trainStart + trainBars;
                int testStart = trainEnd;
                int testEnd = testStart + testBars;

                // Slice data for this window
                TimeSeries trainData = fullData.slice(trainStart, trainEnd);
                TimeSeries testData = fullData.slice(testStart, testEnd);

                // Optimize on training data
                Map<String, Object> optimalParams = optimizeParameters(
                        strategy, trainData, executor
                );

                // Test on out-of-sample data
                BaseStrategy testStrategy = createStrategyWithParams(strategy, optimalParams);
                SimpleBacktestEngine engine = new SimpleBacktestEngine(baseConfig);
                BacktestResult oosResult = engine.run(testStrategy, testData);

                // Calculate statistics
                StatisticsCalculator calculator = new StatisticsCalculator();
                BacktestStatistics oosStats = calculator.calculate(oosResult);

                // Record window result
                WindowResult windowResult = new WindowResult(
                        trainStart, trainEnd, testStart, testEnd,
                        optimalParams, oosResult, oosStats
                );
                windowResults.add(windowResult);
                oosResults.add(oosResult);

                windowStart += stepBars;
            }

        } finally {
            executor.shutdown();
        }

        // Calculate aggregated OOS statistics
        AggregatedStatistics aggregated = aggregateResults(oosResults);

        return new WalkForwardResult(
                windowResults,
                aggregated,
                trainBars,
                testBars,
                stepBars
        );
    }

    /**
     * Optimize strategy parameters on training data.
     */
    private Map<String, Object> optimizeParameters(BaseStrategy strategy, TimeSeries trainData,
                                                    ExecutorService executor) {
        List<StrategyParameter> params = strategy.getParameters();
        if (params.isEmpty()) {
            return Collections.emptyMap();
        }

        // Generate parameter combinations
        List<Map<String, Object>> combinations = generateParameterCombinations(params);

        // Limit number of combinations
        if (combinations.size() > optimizationIterations) {
            Collections.shuffle(combinations);
            combinations = combinations.subList(0, optimizationIterations);
        }

        // Test each combination in parallel
        List<Future<OptimizationResult>> futures = new ArrayList<>();

        for (Map<String, Object> paramSet : combinations) {
            futures.add(executor.submit(() -> {
                BaseStrategy testStrategy = createStrategyWithParams(strategy, paramSet);
                SimpleBacktestEngine engine = new SimpleBacktestEngine(baseConfig);
                BacktestResult result = engine.run(testStrategy, trainData);

                StatisticsCalculator calculator = new StatisticsCalculator();
                BacktestStatistics stats = calculator.calculate(result);

                // Score based on risk-adjusted returns
                double score = calculateOptimizationScore(stats);

                return new OptimizationResult(paramSet, score, stats);
            }));
        }

        // Find best result
        OptimizationResult best = null;
        for (Future<OptimizationResult> future : futures) {
            try {
                OptimizationResult result = future.get();
                if (best == null || result.score > best.score) {
                    best = result;
                }
            } catch (Exception e) {
                // Skip failed optimizations
            }
        }

        return best != null ? best.params : Collections.emptyMap();
    }

    /**
     * Generate parameter combinations for optimization.
     */
    private List<Map<String, Object>> generateParameterCombinations(List<StrategyParameter> params) {
        List<Map<String, Object>> combinations = new ArrayList<>();

        // Start with default values
        Map<String, Object> defaults = new HashMap<>();
        for (StrategyParameter param : params) {
            defaults.put(param.name(), param.defaultValue());
        }
        combinations.add(defaults);

        // Generate grid of values
        for (StrategyParameter param : params) {
            List<Map<String, Object>> newCombinations = new ArrayList<>();

            List<Object> values = getParameterValues(param);
            for (Object value : values) {
                for (Map<String, Object> existing : combinations) {
                    Map<String, Object> newComb = new HashMap<>(existing);
                    newComb.put(param.name(), value);
                    newCombinations.add(newComb);
                }
            }

            combinations = newCombinations;

            // Limit explosion
            if (combinations.size() > optimizationIterations * 10) {
                Collections.shuffle(combinations);
                combinations = combinations.subList(0, optimizationIterations * 10);
            }
        }

        return combinations;
    }

    /**
     * Get candidate values for a parameter.
     */
    private List<Object> getParameterValues(StrategyParameter param) {
        List<Object> values = new ArrayList<>();

        switch (param.type()) {
            case INTEGER -> {
                int min = param.getMinInt();
                int max = param.getMaxInt();
                int step = Math.max(1, (max - min) / 5);
                for (int v = min; v <= max; v += step) {
                    values.add(v);
                }
            }
            case DOUBLE -> {
                double min = param.getMinDouble();
                double max = param.getMaxDouble();
                double step = (max - min) / 5;
                for (double v = min; v <= max; v += step) {
                    values.add(v);
                }
            }
            case BOOLEAN -> {
                values.add(true);
                values.add(false);
            }
            default -> values.add(param.defaultValue());
        }

        return values;
    }

    /**
     * Calculate optimization score (higher is better).
     */
    private double calculateOptimizationScore(BacktestStatistics stats) {
        // Use Sharpe ratio as primary score, with penalties for extreme drawdown
        double sharpe = stats.sharpeRatio();
        double maxDD = stats.maxDrawdownPercent();

        // Penalize high drawdown
        double ddPenalty = maxDD > 20 ? (maxDD - 20) * 0.1 : 0;

        // Penalize too few trades (potential overfitting)
        double tradePenalty = stats.totalTrades() < 10 ? 1.0 : 0;

        return sharpe - ddPenalty - tradePenalty;
    }

    /**
     * Create a new strategy instance with specific parameters.
     */
    private BaseStrategy createStrategyWithParams(BaseStrategy template, Map<String, Object> params) {
        try {
            BaseStrategy newStrategy = template.getClass().getDeclaredConstructor().newInstance();
            newStrategy.setParameters(params);
            return newStrategy;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create strategy instance", e);
        }
    }

    /**
     * Aggregate statistics from all OOS results.
     */
    private AggregatedStatistics aggregateResults(List<BacktestResult> oosResults) {
        if (oosResults.isEmpty()) {
            return new AggregatedStatistics(0, 0, 0, 0, 0, 0, 0);
        }

        double totalReturn = 0;
        double totalTrades = 0;
        double totalWins = 0;
        double maxDD = 0;
        List<Double> returns = new ArrayList<>();

        for (BacktestResult result : oosResults) {
            StatisticsCalculator calc = new StatisticsCalculator();
            BacktestStatistics stats = calc.calculate(result);

            totalReturn += result.getNetReturnPercent();
            totalTrades += stats.totalTrades();
            totalWins += stats.totalTrades() * stats.winRate() / 100.0;
            maxDD = Math.max(maxDD, stats.maxDrawdownPercent());
            returns.add(result.getNetReturnPercent());
        }

        double avgReturn = totalReturn / oosResults.size();
        double winRate = totalTrades > 0 ? (totalWins / totalTrades) * 100 : 0;

        // Calculate return volatility
        double sumSq = 0;
        for (double r : returns) {
            sumSq += (r - avgReturn) * (r - avgReturn);
        }
        double volatility = returns.size() > 1 ? Math.sqrt(sumSq / (returns.size() - 1)) : 0;

        // Sharpe across periods
        double sharpe = volatility > 0 ? avgReturn / volatility : 0;

        return new AggregatedStatistics(
                totalReturn,
                avgReturn,
                (int) totalTrades,
                winRate,
                maxDD,
                volatility,
                sharpe
        );
    }

    /**
     * Result of walk-forward analysis.
     */
    public record WalkForwardResult(
            List<WindowResult> windows,
            AggregatedStatistics aggregated,
            int trainBars,
            int testBars,
            int stepBars
    ) {
        public int getWindowCount() {
            return windows.size();
        }

        public double getTotalOOSReturn() {
            return aggregated.totalReturn();
        }

        public double getAverageOOSReturn() {
            return aggregated.avgReturn();
        }
    }

    /**
     * Result for a single walk-forward window.
     */
    public record WindowResult(
            int trainStart,
            int trainEnd,
            int testStart,
            int testEnd,
            Map<String, Object> optimalParams,
            BacktestResult oosResult,
            BacktestStatistics oosStats
    ) {}

    /**
     * Aggregated statistics across all OOS periods.
     */
    public record AggregatedStatistics(
            double totalReturn,
            double avgReturn,
            int totalTrades,
            double winRate,
            double maxDrawdown,
            double returnVolatility,
            double sharpeRatio
    ) {}

    /**
     * Result of parameter optimization.
     */
    private record OptimizationResult(
            Map<String, Object> params,
            double score,
            BacktestStatistics stats
    ) {}
}
