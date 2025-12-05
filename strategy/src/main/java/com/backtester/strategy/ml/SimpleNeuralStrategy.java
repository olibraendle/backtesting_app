package com.backtester.strategy.ml;

import java.util.List;

import com.backtester.strategy.StrategyParameter;

/**
 * Example ML strategy using a neural network ONNX model.
 *
 * This strategy expects a model trained on:
 * - Input: [batch, lookback * 5 + 5] features (OHLCV + indicators)
 * - Output: [batch, 2] probabilities (down, up)
 *
 * The model file should be provided via setModelPath().
 */
public class SimpleNeuralStrategy extends OnnxStrategy {

    @Override
    public String getName() {
        return "Neural Network";
    }

    @Override
    public String getDescription() {
        return "ML-based strategy using a neural network model (ONNX format). " +
               "Predicts price direction using OHLCV data and technical indicators. " +
               "Strategy controls all stop loss/take profit logic.";
    }

    @Override
    public List<StrategyParameter> getParameters() {
        return List.of(
                StrategyParameter.intParam("lookbackPeriod", "Bars for features", 20, 5, 100, 1),
                StrategyParameter.doubleParam("threshold", "Confidence threshold", 0.65, 0.5, 0.95, 0.05),
                StrategyParameter.doubleParam("riskPercent", "Risk per trade (%)", 1.5, 0.5, 5.0, 0.5),
                StrategyParameter.doubleParam("stopLossAtr", "Stop loss (ATR x)", 1.5, 0.5, 3.0, 0.25),
                StrategyParameter.doubleParam("takeProfitAtr", "Take profit (ATR x)", 2.5, 1.0, 5.0, 0.25)
        );
    }

    @Override
    protected float[] prepareFeatures() {
        int lookback = getIntParam("lookbackPeriod");
        double[][] ohlcv = getOHLCV(lookback);

        if (ohlcv.length < lookback) return null;

        // Feature vector: normalized OHLCV + returns + volatility + momentum
        int featuresPerBar = 8;
        float[] features = new float[lookback * featuresPerBar];

        double lastClose = ohlcv[ohlcv.length - 1][3];
        double[] returns = new double[lookback];
        double[] volatility = new double[lookback];

        // Calculate returns
        for (int i = 1; i < ohlcv.length; i++) {
            returns[i] = (ohlcv[i][3] - ohlcv[i-1][3]) / ohlcv[i-1][3];
        }
        returns[0] = returns[1]; // Fill first

        // Calculate rolling volatility
        for (int i = 0; i < ohlcv.length; i++) {
            double sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - 4); j <= i; j++) {
                sum += returns[j] * returns[j];
                count++;
            }
            volatility[i] = Math.sqrt(sum / count);
        }

        int idx = 0;
        for (int i = 0; i < ohlcv.length; i++) {
            double[] bar = ohlcv[i];

            // Normalized prices (relative to last close)
            features[idx++] = (float) ((bar[0] / lastClose) - 1.0);  // Open
            features[idx++] = (float) ((bar[1] / lastClose) - 1.0);  // High
            features[idx++] = (float) ((bar[2] / lastClose) - 1.0);  // Low
            features[idx++] = (float) ((bar[3] / lastClose) - 1.0);  // Close

            // Volume (log normalized)
            features[idx++] = (float) (Math.log1p(bar[4]) / 25.0 - 0.5);

            // Return
            features[idx++] = (float) (returns[i] * 100); // Scale up for better gradients

            // Volatility
            features[idx++] = (float) (volatility[i] * 100);

            // Bar range (high-low relative to close)
            features[idx++] = (float) ((bar[1] - bar[2]) / bar[3] * 100);
        }

        return features;
    }

    @Override
    protected Signal interpretPrediction(float[] prediction, double threshold) {
        // Model output: [P(down), P(up), P(neutral)] or [P(down), P(up)]
        if (prediction.length >= 2) {
            double probUp = prediction[1];
            double probDown = prediction[0];

            // Require strong confidence and clear direction
            if (probUp > threshold && probUp > probDown * 1.5) {
                return Signal.BUY;
            }
            if (probDown > threshold && probDown > probUp * 1.5) {
                return Signal.SELL;
            }
        }
        return Signal.HOLD;
    }
}
