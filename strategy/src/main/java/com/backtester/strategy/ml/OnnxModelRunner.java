package com.backtester.strategy.ml;

import java.nio.FloatBuffer;
import java.nio.file.Path;

/**
 * ONNX Model Runner for ML-based strategies.
 *
 * Wraps ONNX Runtime to load and run inference on ONNX models.
 * Supports models with single input tensor and single output tensor.
 */
public class OnnxModelRunner implements AutoCloseable {

    private final Path modelPath;
    private final boolean modelLoaded;

    // ONNX Runtime objects (lazy loaded to avoid dependency issues)
    private Object ortEnvironment;
    private Object ortSession;
    private String inputName;
    private long[] inputShape;
    private String outputName;

    /**
     * Create a new ONNX model runner.
     *
     * @param modelPath Path to the ONNX model file
     */
    public OnnxModelRunner(Path modelPath) {
        this.modelPath = modelPath;
        this.modelLoaded = loadModel();
    }

    /**
     * Check if model is loaded successfully.
     */
    public boolean isLoaded() {
        return modelLoaded;
    }

    /**
     * Load the ONNX model.
     */
    private boolean loadModel() {
        try {
            // Use reflection to load ONNX Runtime (optional dependency)
            Class<?> envClass = Class.forName("ai.onnxruntime.OrtEnvironment");
            Class<?> sessionClass = Class.forName("ai.onnxruntime.OrtSession");

            // Get environment
            ortEnvironment = envClass.getMethod("getEnvironment").invoke(null);

            // Create session
            ortSession = envClass.getMethod("createSession", String.class)
                    .invoke(ortEnvironment, modelPath.toString());

            // Get input info
            Object inputInfo = sessionClass.getMethod("getInputInfo").invoke(ortSession);
            var inputMap = (java.util.Map<?, ?>) inputInfo;
            var firstInput = inputMap.entrySet().iterator().next();
            inputName = (String) firstInput.getKey();

            // Get input shape from tensor info
            Object tensorInfo = firstInput.getValue().getClass()
                    .getMethod("getInfo").invoke(firstInput.getValue());
            inputShape = (long[]) tensorInfo.getClass()
                    .getMethod("getShape").invoke(tensorInfo);

            // Get output info
            Object outputInfo = sessionClass.getMethod("getOutputInfo").invoke(ortSession);
            var outputMap = (java.util.Map<?, ?>) outputInfo;
            outputName = (String) outputMap.keySet().iterator().next();

            return true;

        } catch (ClassNotFoundException e) {
            // ONNX Runtime not available
            System.err.println("ONNX Runtime not found. ML strategies require onnxruntime dependency.");
            return false;
        } catch (Exception e) {
            System.err.println("Failed to load ONNX model: " + e.getMessage());
            return false;
        }
    }

    /**
     * Run inference on the model.
     *
     * @param input Input features as float array
     * @return Model output as float array, or null on error
     */
    public float[] predict(float[] input) {
        if (!modelLoaded) return null;

        try {
            // Create input tensor
            Class<?> tensorClass = Class.forName("ai.onnxruntime.OnnxTensor");
            Class<?> envClass = Class.forName("ai.onnxruntime.OrtEnvironment");

            // Reshape input to match model expectations
            long[] shape = inputShape.clone();
            if (shape[0] == -1) shape[0] = 1; // Batch size

            FloatBuffer buffer = FloatBuffer.wrap(input);
            Object inputTensor = tensorClass.getMethod("createTensor",
                            envClass, FloatBuffer.class, long[].class)
                    .invoke(null, ortEnvironment, buffer, shape);

            // Create input map
            java.util.Map<String, Object> inputs = java.util.Map.of(inputName, inputTensor);

            // Run inference
            Class<?> sessionClass = Class.forName("ai.onnxruntime.OrtSession");
            Object result = sessionClass.getMethod("run", java.util.Map.class)
                    .invoke(ortSession, inputs);

            // Get output
            var resultMap = (java.util.Map<?, ?>) result;
            Object outputTensor = resultMap.get(outputName);

            // Extract float array from output
            float[][] output2D = (float[][]) outputTensor.getClass()
                    .getMethod("getValue").invoke(outputTensor);

            // Clean up tensors
            inputTensor.getClass().getMethod("close").invoke(inputTensor);
            outputTensor.getClass().getMethod("close").invoke(outputTensor);

            return output2D[0]; // Return first batch

        } catch (Exception e) {
            System.err.println("Inference error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get the model's expected input shape.
     */
    public long[] getInputShape() {
        return inputShape != null ? inputShape.clone() : new long[0];
    }

    /**
     * Get the model path.
     */
    public Path getModelPath() {
        return modelPath;
    }

    @Override
    public void close() {
        if (ortSession != null) {
            try {
                ortSession.getClass().getMethod("close").invoke(ortSession);
            } catch (Exception ignored) {
            }
            ortSession = null;
        }
    }
}
