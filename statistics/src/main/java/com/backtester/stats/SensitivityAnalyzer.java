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
 * Parameter Sensitivity Analyzer for generating heatmaps.
 *
 * Tests strategy performance across parameter combinations to identify:
 * - Optimal parameter regions
 * - Parameter sensitivity (robustness)
 * - Potential overfitting (isolated peaks)
 */
public class SensitivityAnalyzer {

    private final BacktestConfig baseConfig;
    private final int gridSize;

    /**
     * Create a sensitivity analyzer.
     *
     * @param baseConfig Base backtest configuration
     * @param gridSize Number of values to test for each parameter dimension
     */
    public SensitivityAnalyzer(BacktestConfig baseConfig, int gridSize) {
        this.baseConfig = baseConfig;
        this.gridSize = gridSize;
    }

    /**
     * Create with default grid size.
     */
    public SensitivityAnalyzer(BacktestConfig baseConfig) {
        this(baseConfig, 10);
    }

    /**
     * Analyze sensitivity for two parameters (2D heatmap).
     */
    public HeatmapResult analyze2D(BaseStrategy strategy, TimeSeries data,
                                    String param1Name, String param2Name,
                                    Metric metric) {

        // Find parameter definitions
        List<StrategyParameter> params = strategy.getParameters();
        StrategyParameter param1 = findParameter(params, param1Name);
        StrategyParameter param2 = findParameter(params, param2Name);

        if (param1 == null || param2 == null) {
            throw new IllegalArgumentException("Parameter not found");
        }

        // Generate parameter values
        double[] param1Values = generateValues(param1);
        double[] param2Values = generateValues(param2);

        // Initialize result grid
        double[][] results = new double[param1Values.length][param2Values.length];
        BacktestStatistics[][] stats = new BacktestStatistics[param1Values.length][param2Values.length];

        // Run backtests in parallel
        ExecutorService executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );

        List<Future<GridCell>> futures = new ArrayList<>();

        for (int i = 0; i < param1Values.length; i++) {
            for (int j = 0; j < param2Values.length; j++) {
                final int fi = i;
                final int fj = j;
                final double v1 = param1Values[i];
                final double v2 = param2Values[j];

                futures.add(executor.submit(() -> {
                    Map<String, Object> testParams = new HashMap<>(strategy.getParameterValues());
                    testParams.put(param1Name, convertValue(param1, v1));
                    testParams.put(param2Name, convertValue(param2, v2));

                    BaseStrategy testStrategy = createStrategyWithParams(strategy, testParams);
                    SimpleBacktestEngine engine = new SimpleBacktestEngine(baseConfig);
                    BacktestResult result = engine.run(testStrategy, data);

                    StatisticsCalculator calculator = new StatisticsCalculator();
                    BacktestStatistics calcStats = calculator.calculate(result);

                    double metricValue = extractMetric(calcStats, result, metric);

                    return new GridCell(fi, fj, metricValue, calcStats);
                }));
            }
        }

        // Collect results
        executor.shutdown();
        for (Future<GridCell> future : futures) {
            try {
                GridCell cell = future.get();
                results[cell.i][cell.j] = cell.value;
                stats[cell.i][cell.j] = cell.stats;
            } catch (Exception e) {
                // Mark failed cells as NaN
            }
        }

        // Find optimal point
        int bestI = 0, bestJ = 0;
        double bestValue = metric == Metric.MAX_DRAWDOWN ? Double.MAX_VALUE : Double.MIN_VALUE;
        for (int i = 0; i < results.length; i++) {
            for (int j = 0; j < results[0].length; j++) {
                boolean isBetter = metric == Metric.MAX_DRAWDOWN
                        ? results[i][j] < bestValue
                        : results[i][j] > bestValue;
                if (isBetter) {
                    bestValue = results[i][j];
                    bestI = i;
                    bestJ = j;
                }
            }
        }

        // Calculate plateau analysis
        PlateauAnalysis plateau = analyzePlateau(results, param1Values, param2Values, metric);

        return new HeatmapResult(
                param1Name, param2Name,
                param1Values, param2Values,
                results, stats, metric,
                bestI, bestJ, bestValue,
                plateau
        );
    }

    /**
     * Analyze sensitivity for a single parameter (1D).
     */
    public SweepResult analyze1D(BaseStrategy strategy, TimeSeries data,
                                  String paramName, Metric metric) {

        List<StrategyParameter> params = strategy.getParameters();
        StrategyParameter param = findParameter(params, paramName);

        if (param == null) {
            throw new IllegalArgumentException("Parameter not found: " + paramName);
        }

        double[] values = generateValues(param);
        double[] results = new double[values.length];
        BacktestStatistics[] stats = new BacktestStatistics[values.length];

        // Run backtests
        ExecutorService executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );

        List<Future<int[]>> indexFutures = new ArrayList<>();

        for (int i = 0; i < values.length; i++) {
            final int fi = i;
            final double v = values[i];

            indexFutures.add(executor.submit(() -> {
                Map<String, Object> testParams = new HashMap<>(strategy.getParameterValues());
                testParams.put(paramName, convertValue(param, v));

                BaseStrategy testStrategy = createStrategyWithParams(strategy, testParams);
                SimpleBacktestEngine engine = new SimpleBacktestEngine(baseConfig);
                BacktestResult result = engine.run(testStrategy, data);

                StatisticsCalculator calculator = new StatisticsCalculator();
                BacktestStatistics calcStats = calculator.calculate(result);

                double metricValue = extractMetric(calcStats, result, metric);
                results[fi] = metricValue;
                stats[fi] = calcStats;

                return new int[]{fi};
            }));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Find optimal
        int bestIdx = 0;
        double bestValue = metric == Metric.MAX_DRAWDOWN ? Double.MAX_VALUE : Double.MIN_VALUE;
        for (int i = 0; i < results.length; i++) {
            boolean isBetter = metric == Metric.MAX_DRAWDOWN
                    ? results[i] < bestValue
                    : results[i] > bestValue;
            if (isBetter) {
                bestValue = results[i];
                bestIdx = i;
            }
        }

        return new SweepResult(
                paramName, values, results, stats, metric,
                bestIdx, bestValue
        );
    }

    /**
     * Find parameter by name.
     */
    private StrategyParameter findParameter(List<StrategyParameter> params, String name) {
        return params.stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Generate values for a parameter.
     */
    private double[] generateValues(StrategyParameter param) {
        double[] values = new double[gridSize];

        switch (param.type()) {
            case INTEGER -> {
                int min = param.getMinInt();
                int max = param.getMaxInt();
                for (int i = 0; i < gridSize; i++) {
                    values[i] = min + (max - min) * i / (gridSize - 1.0);
                }
            }
            case DOUBLE -> {
                double min = param.getMinDouble();
                double max = param.getMaxDouble();
                for (int i = 0; i < gridSize; i++) {
                    values[i] = min + (max - min) * i / (gridSize - 1.0);
                }
            }
            default -> {
                // For non-numeric types, just use index
                for (int i = 0; i < gridSize; i++) {
                    values[i] = i;
                }
            }
        }

        return values;
    }

    /**
     * Convert double value back to appropriate type.
     */
    private Object convertValue(StrategyParameter param, double value) {
        return switch (param.type()) {
            case INTEGER -> (int) Math.round(value);
            case DOUBLE -> value;
            case BOOLEAN -> value > 0.5;
            default -> param.defaultValue();
        };
    }

    /**
     * Extract metric value from statistics.
     */
    private double extractMetric(BacktestStatistics stats, BacktestResult result, Metric metric) {
        return switch (metric) {
            case NET_RETURN -> result.getNetReturnPercent();
            case SHARPE_RATIO -> stats.sharpeRatio();
            case SORTINO_RATIO -> stats.sortinoRatio();
            case PROFIT_FACTOR -> stats.profitFactor();
            case MAX_DRAWDOWN -> stats.maxDrawdownPercent();
            case WIN_RATE -> stats.winRate();
            case CALMAR_RATIO -> stats.calmarRatio();
            case EXPECTANCY -> stats.expectancy();
        };
    }

    /**
     * Create strategy with specific parameters.
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
     * Analyze the plateau around optimal point.
     */
    private PlateauAnalysis analyzePlateau(double[][] results, double[] param1Values,
                                            double[] param2Values, Metric metric) {
        // Find optimal
        double optimalValue = metric == Metric.MAX_DRAWDOWN ? Double.MAX_VALUE : Double.MIN_VALUE;
        for (double[] row : results) {
            for (double val : row) {
                if (metric == Metric.MAX_DRAWDOWN) {
                    optimalValue = Math.min(optimalValue, val);
                } else {
                    optimalValue = Math.max(optimalValue, val);
                }
            }
        }

        // Count cells within threshold of optimal
        double threshold = Math.abs(optimalValue * 0.1); // 10% of optimal
        int plateauCells = 0;
        double minVal = Double.MAX_VALUE, maxVal = Double.MIN_VALUE;

        for (double[] row : results) {
            for (double val : row) {
                if (Math.abs(val - optimalValue) <= threshold) {
                    plateauCells++;
                }
                minVal = Math.min(minVal, val);
                maxVal = Math.max(maxVal, val);
            }
        }

        double plateauPercent = 100.0 * plateauCells / (results.length * results[0].length);
        double sensitivity = (maxVal - minVal) / Math.abs(optimalValue + 0.0001);

        // Robustness: larger plateau = more robust
        String robustness = plateauPercent > 30 ? "High" : plateauPercent > 15 ? "Medium" : "Low";

        return new PlateauAnalysis(plateauPercent, sensitivity, robustness, minVal, maxVal);
    }

    /**
     * Metrics that can be optimized.
     */
    public enum Metric {
        NET_RETURN("Net Return %"),
        SHARPE_RATIO("Sharpe Ratio"),
        SORTINO_RATIO("Sortino Ratio"),
        PROFIT_FACTOR("Profit Factor"),
        MAX_DRAWDOWN("Max Drawdown %"),
        WIN_RATE("Win Rate %"),
        CALMAR_RATIO("Calmar Ratio"),
        EXPECTANCY("Expectancy");

        private final String displayName;

        Metric(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Result of 2D sensitivity analysis (heatmap data).
     */
    public record HeatmapResult(
            String param1Name,
            String param2Name,
            double[] param1Values,
            double[] param2Values,
            double[][] values,
            BacktestStatistics[][] stats,
            Metric metric,
            int optimalI,
            int optimalJ,
            double optimalValue,
            PlateauAnalysis plateau
    ) {
        public double getOptimalParam1() {
            return param1Values[optimalI];
        }

        public double getOptimalParam2() {
            return param2Values[optimalJ];
        }
    }

    /**
     * Result of 1D parameter sweep.
     */
    public record SweepResult(
            String paramName,
            double[] values,
            double[] results,
            BacktestStatistics[] stats,
            Metric metric,
            int optimalIndex,
            double optimalValue
    ) {
        public double getOptimalParamValue() {
            return values[optimalIndex];
        }
    }

    /**
     * Analysis of the plateau around optimal.
     */
    public record PlateauAnalysis(
            double plateauPercent,
            double sensitivity,
            String robustness,
            double minValue,
            double maxValue
    ) {}

    /**
     * Internal grid cell result.
     */
    private record GridCell(int i, int j, double value, BacktestStatistics stats) {}
}
