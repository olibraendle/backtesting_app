package com.backtester.stats;

import com.backtester.core.portfolio.Trade;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Monte Carlo simulation for strategy robustness analysis.
 * Shuffles trade sequence to estimate distribution of outcomes.
 */
public class MonteCarloSimulator {

    private final int numSimulations;
    private final double initialEquity;

    public MonteCarloSimulator(int numSimulations, double initialEquity) {
        this.numSimulations = numSimulations;
        this.initialEquity = initialEquity;
    }

    public MonteCarloSimulator(double initialEquity) {
        this(10000, initialEquity);
    }

    /**
     * Run Monte Carlo simulation on a list of trades.
     */
    public MonteCarloResult simulate(List<Trade> trades) {
        if (trades.isEmpty()) {
            return MonteCarloResult.empty();
        }

        double[] finalEquities = new double[numSimulations];
        double[] maxDrawdowns = new double[numSimulations];
        double[] maxDrawdownPercents = new double[numSimulations];
        int ruinCount = 0;

        List<Double> tradePnls = trades.stream()
                .map(Trade::netPnl)
                .toList();

        for (int i = 0; i < numSimulations; i++) {
            List<Double> shuffled = shuffleList(tradePnls);
            SimulationResult result = runSimulation(shuffled);

            finalEquities[i] = result.finalEquity;
            maxDrawdowns[i] = result.maxDrawdown;
            maxDrawdownPercents[i] = result.maxDrawdownPercent;

            if (result.finalEquity < initialEquity * 0.5) {
                ruinCount++;
            }
        }

        // Sort for percentile calculations
        Arrays.sort(finalEquities);
        Arrays.sort(maxDrawdowns);
        Arrays.sort(maxDrawdownPercents);

        return new MonteCarloResult(
                numSimulations,
                initialEquity,
                // Final equity percentiles
                percentile(finalEquities, 5),
                percentile(finalEquities, 25),
                percentile(finalEquities, 50),
                percentile(finalEquities, 75),
                percentile(finalEquities, 95),
                mean(finalEquities),
                stdDev(finalEquities),
                // Max drawdown percentiles
                percentile(maxDrawdownPercents, 5),
                percentile(maxDrawdownPercents, 50),
                percentile(maxDrawdownPercents, 95),
                mean(maxDrawdownPercents),
                // Ruin probability
                (double) ruinCount / numSimulations * 100,
                // Raw data for visualization
                finalEquities,
                maxDrawdownPercents
        );
    }

    /**
     * Run a single simulation with shuffled trades.
     */
    private SimulationResult runSimulation(List<Double> tradePnls) {
        double equity = initialEquity;
        double peak = equity;
        double maxDrawdown = 0;
        double maxDrawdownPercent = 0;

        for (double pnl : tradePnls) {
            equity += pnl;

            if (equity > peak) {
                peak = equity;
            } else {
                double drawdown = peak - equity;
                double ddPercent = peak > 0 ? (drawdown / peak) * 100 : 0;
                if (ddPercent > maxDrawdownPercent) {
                    maxDrawdown = drawdown;
                    maxDrawdownPercent = ddPercent;
                }
            }
        }

        return new SimulationResult(equity, maxDrawdown, maxDrawdownPercent);
    }

    /**
     * Shuffle a list (Fisher-Yates).
     */
    private List<Double> shuffleList(List<Double> original) {
        List<Double> shuffled = new ArrayList<>(original);
        Random rng = ThreadLocalRandom.current();
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Double temp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, temp);
        }
        return shuffled;
    }

    private double percentile(double[] sorted, double p) {
        if (sorted.length == 0) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private double mean(double[] values) {
        if (values.length == 0) return 0;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private double stdDev(double[] values) {
        if (values.length < 2) return 0;
        double m = mean(values);
        double sumSq = 0;
        for (double v : values) sumSq += (v - m) * (v - m);
        return Math.sqrt(sumSq / (values.length - 1));
    }

    private record SimulationResult(double finalEquity, double maxDrawdown, double maxDrawdownPercent) {}

    /**
     * Results from Monte Carlo simulation.
     */
    public record MonteCarloResult(
            int numSimulations,
            double initialEquity,
            // Final equity percentiles
            double equity5thPercentile,
            double equity25thPercentile,
            double equity50thPercentile,
            double equity75thPercentile,
            double equity95thPercentile,
            double equityMean,
            double equityStdDev,
            // Max drawdown percentiles
            double maxDD5thPercentile,
            double maxDD50thPercentile,
            double maxDD95thPercentile,
            double maxDDMean,
            // Risk
            double ruinProbability,
            // Raw data
            double[] finalEquities,
            double[] maxDrawdowns
    ) {
        public static MonteCarloResult empty() {
            return new MonteCarloResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    new double[0], new double[0]);
        }

        /**
         * Get the expected return range (5th to 95th percentile).
         */
        public String getReturnRange() {
            double low = ((equity5thPercentile - initialEquity) / initialEquity) * 100;
            double high = ((equity95thPercentile - initialEquity) / initialEquity) * 100;
            return String.format("%.1f%% to %.1f%%", low, high);
        }

        /**
         * Get the expected drawdown range.
         */
        public String getDrawdownRange() {
            return String.format("%.1f%% to %.1f%%", maxDD5thPercentile, maxDD95thPercentile);
        }
    }
}
