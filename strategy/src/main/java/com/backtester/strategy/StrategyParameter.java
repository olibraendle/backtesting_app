package com.backtester.strategy;

/**
 * Defines a configurable parameter for a strategy.
 */
public record StrategyParameter(
        String name,
        String description,
        ParameterType type,
        Object defaultValue,
        Object minValue,
        Object maxValue,
        Object step
) {
    public enum ParameterType {
        INTEGER,
        DOUBLE,
        BOOLEAN,
        STRING,
        ENUM
    }

    // ===== Factory Methods =====

    public static StrategyParameter intParam(String name, String description, int defaultValue, int min, int max) {
        return new StrategyParameter(name, description, ParameterType.INTEGER, defaultValue, min, max, 1);
    }

    public static StrategyParameter intParam(String name, String description, int defaultValue, int min, int max, int step) {
        return new StrategyParameter(name, description, ParameterType.INTEGER, defaultValue, min, max, step);
    }

    public static StrategyParameter doubleParam(String name, String description, double defaultValue, double min, double max) {
        return new StrategyParameter(name, description, ParameterType.DOUBLE, defaultValue, min, max, 0.1);
    }

    public static StrategyParameter doubleParam(String name, String description, double defaultValue, double min, double max, double step) {
        return new StrategyParameter(name, description, ParameterType.DOUBLE, defaultValue, min, max, step);
    }

    public static StrategyParameter boolParam(String name, String description, boolean defaultValue) {
        return new StrategyParameter(name, description, ParameterType.BOOLEAN, defaultValue, null, null, null);
    }

    public static StrategyParameter stringParam(String name, String description, String defaultValue) {
        return new StrategyParameter(name, description, ParameterType.STRING, defaultValue, null, null, null);
    }

    // ===== Value Extraction =====

    public int getDefaultInt() {
        return ((Number) defaultValue).intValue();
    }

    public double getDefaultDouble() {
        return ((Number) defaultValue).doubleValue();
    }

    public boolean getDefaultBoolean() {
        return (Boolean) defaultValue;
    }

    public String getDefaultString() {
        return (String) defaultValue;
    }

    public int getMinInt() {
        return minValue != null ? ((Number) minValue).intValue() : Integer.MIN_VALUE;
    }

    public int getMaxInt() {
        return maxValue != null ? ((Number) maxValue).intValue() : Integer.MAX_VALUE;
    }

    public double getMinDouble() {
        return minValue != null ? ((Number) minValue).doubleValue() : Double.MIN_VALUE;
    }

    public double getMaxDouble() {
        return maxValue != null ? ((Number) maxValue).doubleValue() : Double.MAX_VALUE;
    }

    public int getStepInt() {
        return step != null ? ((Number) step).intValue() : 1;
    }

    public double getStepDouble() {
        return step != null ? ((Number) step).doubleValue() : 0.1;
    }

    @Override
    public String toString() {
        return String.format("%s (%s) = %s [%s to %s]", name, type, defaultValue, minValue, maxValue);
    }
}
