package com.backtester.strategy.ml;

import com.backtester.common.model.Bar;
import com.backtester.strategy.BaseStrategy;
import com.backtester.strategy.StrategyParameter;

import java.nio.file.Path;
import java.util.List;

/**
 * Base class for ML strategies using ONNX models.
 *
 * Strategy has FULL control over:
 * - Feature engineering (what data to feed the model)
 * - Model inference timing
 * - Signal interpretation
 * - Position sizing, stop loss, take profit
 *
 * Subclasses implement:
 * - prepareFeatures() - Convert market data to model input
 * - interpretPrediction() - Convert model output to trading signal
 */
public abstract class OnnxStrategy extends BaseStrategy {

    protected OnnxModelRunner modelRunner;
    protected Path modelPath;
    protected int lookbackPeriod;

    // State tracking for ML-based trades
    protected double entryPrice;
    protected double stopLoss;
    protected double takeProfit;

    @Override
    public String getDescription() {
        return "ML-based strategy using ONNX model for predictions. " +
               "Full control over feature engineering and trade execution.";
    }

    @Override
    public List<StrategyParameter> getParameters() {
        return List.of(
                StrategyParameter.intParam("lookbackPeriod", "Bars for feature calculation", 20, 5, 100, 1),
                StrategyParameter.doubleParam("threshold", "Signal threshold", 0.6, 0.5, 0.95, 0.05),
                StrategyParameter.doubleParam("riskPercent", "Risk per trade (%)", 2.0, 0.5, 10.0, 0.5),
                StrategyParameter.doubleParam("stopLossAtr", "Stop loss (ATR multiplier)", 2.0, 0.5, 5.0, 0.5),
                StrategyParameter.doubleParam("takeProfitAtr", "Take profit (ATR multiplier)", 3.0, 1.0, 10.0, 0.5)
        );
    }

    @Override
    public int getWarmupBars() {
        return getIntParam("lookbackPeriod") + 14;
    }

    @Override
    protected void onInitialize() {
        lookbackPeriod = getIntParam("lookbackPeriod");

        // Initialize model if path is set
        if (modelPath != null) {
            try {
                modelRunner = new OnnxModelRunner(modelPath);
            } catch (Exception e) {
                log("Failed to load ONNX model: " + e.getMessage());
                modelRunner = null;
            }
        }

        entryPrice = 0;
        stopLoss = 0;
        takeProfit = 0;
    }

    /**
     * Set the path to the ONNX model file.
     */
    public void setModelPath(Path path) {
        this.modelPath = path;
    }

    @Override
    public void onBar(Bar bar) {
        if (modelRunner == null) {
            return; // No model loaded
        }

        double atrValue = atr(14);
        if (Double.isNaN(atrValue)) return;

        // Prepare features for model
        float[] features = prepareFeatures();
        if (features == null) return;

        // Run model inference
        float[] prediction = modelRunner.predict(features);
        if (prediction == null) return;

        // Get parameters
        double threshold = getDoubleParam("threshold");
        double riskPercent = getDoubleParam("riskPercent");
        double stopLossAtr = getDoubleParam("stopLossAtr");
        double takeProfitAtr = getDoubleParam("takeProfitAtr");

        // Interpret prediction and generate signal
        Signal signal = interpretPrediction(prediction, threshold);

        if (hasPosition()) {
            // === EXIT LOGIC (strategy controlled) ===
            double high = bar.high();
            double low = bar.low();

            // Check stop loss
            if (isLong() && low <= stopLoss) {
                closePositionAt(stopLoss);
                logTrade("STOP LOSS", getPositionSize(), stopLoss, "ML Strategy");
            } else if (isShort() && high >= stopLoss) {
                closePositionAt(stopLoss);
                logTrade("STOP LOSS", getPositionSize(), stopLoss, "ML Strategy");
            }
            // Check take profit
            else if (isLong() && high >= takeProfit) {
                closePositionAt(takeProfit);
                logTrade("TAKE PROFIT", getPositionSize(), takeProfit, "ML Strategy");
            } else if (isShort() && low <= takeProfit) {
                closePositionAt(takeProfit);
                logTrade("TAKE PROFIT", getPositionSize(), takeProfit, "ML Strategy");
            }
            // Check signal-based exit
            else if ((isLong() && signal == Signal.SELL) || (isShort() && signal == Signal.BUY)) {
                closePosition();
                logTrade("EXIT", getPositionSize(), bar.close(), "ML signal reversal");
            }

        } else {
            // === ENTRY LOGIC (strategy controlled) ===
            if (signal == Signal.BUY) {
                double stopDistance = atrValue * stopLossAtr;
                double quantity = quantityForRisk(riskPercent, stopDistance);

                if (quantity > 0) {
                    double fillPrice = buy(quantity);
                    entryPrice = fillPrice;
                    stopLoss = fillPrice - stopDistance;
                    takeProfit = fillPrice + atrValue * takeProfitAtr;
                    logTrade("ENTRY LONG", quantity, fillPrice,
                            String.format("ML confidence=%.2f", prediction[0]));
                }

            } else if (signal == Signal.SELL) {
                double stopDistance = atrValue * stopLossAtr;
                double quantity = quantityForRisk(riskPercent, stopDistance);

                if (quantity > 0) {
                    double fillPrice = sell(quantity);
                    entryPrice = fillPrice;
                    stopLoss = fillPrice + stopDistance;
                    takeProfit = fillPrice - atrValue * takeProfitAtr;
                    logTrade("ENTRY SHORT", quantity, fillPrice,
                            String.format("ML confidence=%.2f", prediction.length > 1 ? prediction[1] : prediction[0]));
                }
            }
        }
    }

    /**
     * Prepare input features for the model.
     * Override to customize feature engineering.
     */
    protected float[] prepareFeatures() {
        // Default: OHLCV + technical indicators
        double[][] ohlcv = getOHLCV(lookbackPeriod);
        if (ohlcv.length < lookbackPeriod) return null;

        // Flatten OHLCV + add technical indicators
        int featuresPerBar = 5; // O, H, L, C, V
        int techIndicators = 5; // SMA, EMA, RSI, ATR, MACD
        float[] features = new float[lookbackPeriod * featuresPerBar + techIndicators];

        int idx = 0;

        // Normalize OHLCV relative to latest close
        double lastClose = ohlcv[ohlcv.length - 1][3];

        for (double[] bar : ohlcv) {
            features[idx++] = (float) (bar[0] / lastClose); // Open (normalized)
            features[idx++] = (float) (bar[1] / lastClose); // High (normalized)
            features[idx++] = (float) (bar[2] / lastClose); // Low (normalized)
            features[idx++] = (float) (bar[3] / lastClose); // Close (normalized)
            features[idx++] = (float) (Math.log1p(bar[4]) / 20.0); // Volume (log scaled)
        }

        // Add technical indicators (normalized to 0-1 range where applicable)
        features[idx++] = (float) (sma(lookbackPeriod) / lastClose);
        features[idx++] = (float) (ema(lookbackPeriod) / lastClose);
        features[idx++] = (float) (rsi(14) / 100.0);
        features[idx++] = (float) (atr(14) / lastClose);
        features[idx++] = (float) (macd(12, 26) / lastClose);

        return features;
    }

    /**
     * Interpret model prediction to generate trading signal.
     * Override to customize signal interpretation.
     *
     * @param prediction Model output
     * @param threshold Confidence threshold
     * @return Trading signal
     */
    protected Signal interpretPrediction(float[] prediction, double threshold) {
        // Default: binary classification [P(down), P(up)] or single [P(up)]
        if (prediction.length == 1) {
            // Single output: probability of going up
            if (prediction[0] > threshold) return Signal.BUY;
            if (prediction[0] < (1 - threshold)) return Signal.SELL;
        } else if (prediction.length >= 2) {
            // Two outputs: [P(down), P(up)]
            if (prediction[1] > threshold) return Signal.BUY;
            if (prediction[0] > threshold) return Signal.SELL;
        }
        return Signal.HOLD;
    }

    @Override
    public void onEnd() {
        // Clean up model resources
        if (modelRunner != null) {
            modelRunner.close();
            modelRunner = null;
        }
    }

    /**
     * Trading signal enum.
     */
    public enum Signal {
        BUY,
        SELL,
        HOLD
    }
}
