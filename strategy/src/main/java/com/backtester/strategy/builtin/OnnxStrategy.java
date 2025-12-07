package com.backtester.strategy.builtin;

import ai.onnxruntime.*;
import com.backtester.common.model.Bar;
import com.backtester.strategy.BaseStrategy;
import com.backtester.strategy.StrategyParameter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * ONNX Model Strategy
 *
 * Loads a pre-trained ONNX model and uses it for trading decisions.
 *
 * The model should:
 * - Accept input shape: [1, numFeatures] where numFeatures = lookback * 5 (OHLCV)
 * - Output shape: [1, 1] with value between 0 and 1
 *   - > 0.5 = bullish signal (buy)
 *   - < 0.5 = bearish signal (sell/exit)
 *
 * To use:
 * 1. Train your model in Python (TensorFlow, PyTorch, scikit-learn)
 * 2. Export to ONNX format
 * 3. Set the model path parameter
 */
public class OnnxStrategy extends BaseStrategy {

    // Parameters
    private String modelPath;
    private int lookbackBars;
    private double threshold;
    private double positionSizePercent;

    // ONNX Runtime
    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;
    private boolean modelLoaded = false;

    // Feature buffer
    private double[][] featureBuffer;
    private int bufferIndex = 0;

    @Override
    public String getName() {
        return "ONNX ML Strategy";
    }

    @Override
    public String getDescription() {
        return "Machine learning strategy using ONNX models. Load models trained in Python/TensorFlow/PyTorch.";
    }

    @Override
    public List<StrategyParameter> getParameters() {
        return List.of(
            StrategyParameter.stringParam("modelPath", "Path to ONNX model file", "model.onnx"),
            StrategyParameter.intParam("lookbackBars", "Number of bars for features", 20, 5, 100, 5),
            StrategyParameter.doubleParam("threshold", "Signal threshold (0.5 = neutral)", 0.5, 0.1, 0.9, 0.05),
            StrategyParameter.doubleParam("positionSize", "Position size (% of equity)", 100, 10, 100, 10)
        );
    }

    @Override
    public int getWarmupBars() {
        return getIntParam("lookbackBars") + 10;
    }

    @Override
    protected void onInitialize() {
        modelPath = getStringParam("modelPath");
        lookbackBars = getIntParam("lookbackBars");
        threshold = getDoubleParam("threshold");
        positionSizePercent = getDoubleParam("positionSize");

        // Initialize feature buffer: [lookback][5 features: O,H,L,C,V]
        featureBuffer = new double[lookbackBars][5];
        bufferIndex = 0;

        // Try to load ONNX model
        loadModel();
    }

    private void loadModel() {
        try {
            Path path = Path.of(modelPath);
            if (!Files.exists(path)) {
                System.err.println("ONNX model not found: " + modelPath);
                System.err.println("Strategy will use fallback logic (SMA crossover)");
                modelLoaded = false;
                return;
            }

            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            session = env.createSession(modelPath, opts);

            // Get input name
            inputName = session.getInputNames().iterator().next();

            modelLoaded = true;
            System.out.println("ONNX model loaded successfully: " + modelPath);
            System.out.println("Input name: " + inputName);

        } catch (Exception e) {
            System.err.println("Failed to load ONNX model: " + e.getMessage());
            System.err.println("Strategy will use fallback logic (SMA crossover)");
            modelLoaded = false;
        }
    }

    @Override
    public void onBar(Bar bar) {
        // Update feature buffer with normalized OHLCV
        updateFeatureBuffer(bar);

        // Need enough data
        if (bufferIndex < lookbackBars) {
            return;
        }

        // Get prediction
        double signal;
        if (modelLoaded) {
            signal = runInference();
        } else {
            // Fallback to simple SMA logic if model not loaded
            signal = fallbackSignal();
        }

        // Trading logic based on signal
        if (hasPosition()) {
            // Exit if signal below threshold
            if (signal < threshold) {
                closePosition();
            }
        } else {
            // Enter if signal above threshold
            if (signal > threshold) {
                double quantity = Math.floor(quantityForPercent(positionSizePercent));
                if (quantity >= 1) {
                    buy(quantity);
                }
            }
        }
    }

    private void updateFeatureBuffer(Bar bar) {
        // Shift buffer
        if (bufferIndex >= lookbackBars) {
            for (int i = 0; i < lookbackBars - 1; i++) {
                featureBuffer[i] = featureBuffer[i + 1];
            }
            bufferIndex = lookbackBars - 1;
        }

        // Normalize prices relative to close (percentage change)
        double close = bar.close();
        featureBuffer[bufferIndex][0] = (bar.open() - close) / close;   // Open relative to close
        featureBuffer[bufferIndex][1] = (bar.high() - close) / close;   // High relative to close
        featureBuffer[bufferIndex][2] = (bar.low() - close) / close;    // Low relative to close
        featureBuffer[bufferIndex][3] = 0;                               // Close is reference (0)
        featureBuffer[bufferIndex][4] = Math.log1p(bar.volume()) / 20;  // Log-normalized volume

        bufferIndex++;
    }

    private double runInference() {
        try {
            // Flatten feature buffer to 1D array
            int numFeatures = lookbackBars * 5;
            float[][] input = new float[1][numFeatures];

            int idx = 0;
            for (int i = 0; i < lookbackBars; i++) {
                for (int j = 0; j < 5; j++) {
                    input[0][idx++] = (float) featureBuffer[i][j];
                }
            }

            // Create tensor and run inference
            OnnxTensor tensor = OnnxTensor.createTensor(env, input);
            OrtSession.Result result = session.run(Map.of(inputName, tensor));

            // Get output (assuming single output with shape [1,1] or [1])
            Object outputValue = result.get(0).getValue();

            double prediction;
            if (outputValue instanceof float[][]) {
                prediction = ((float[][]) outputValue)[0][0];
            } else if (outputValue instanceof float[]) {
                prediction = ((float[]) outputValue)[0];
            } else {
                prediction = 0.5; // Neutral
            }

            tensor.close();
            result.close();

            return prediction;

        } catch (Exception e) {
            System.err.println("Inference error: " + e.getMessage());
            return 0.5; // Neutral on error
        }
    }

    private double fallbackSignal() {
        // Simple momentum-based fallback when model not available
        double fastSma = sma(5);
        double slowSma = sma(20);

        if (Double.isNaN(fastSma) || Double.isNaN(slowSma)) {
            return 0.5;
        }

        // Convert to 0-1 signal
        double diff = (fastSma - slowSma) / slowSma;
        return 0.5 + (diff * 10); // Scale difference
    }

    @Override
    public void onEnd() {
        if (hasPosition()) {
            closePosition();
        }

        // Clean up ONNX resources
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {}
        }
    }
}
