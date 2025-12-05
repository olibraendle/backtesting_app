package com.backtester.strategy;

import com.backtester.strategy.builtin.*;
import com.backtester.strategy.ml.SimpleNeuralStrategy;

import java.util.*;
import java.util.function.Supplier;

/**
 * Registry of available strategies.
 * Supports both built-in strategies and dynamically loaded strategies.
 */
public class StrategyRegistry {

    private static final Map<String, Supplier<BaseStrategy>> STRATEGIES = new LinkedHashMap<>();

    static {
        // Register built-in strategies
        register("SMA Crossover", SmaStrategy.class);
        register("RSI Mean Reversion", RsiStrategy.class);
        register("Donchian Breakout", BreakoutStrategy.class);
        register("MACD Signal", MacdStrategy.class);
        register("Neural Network (ML)", SimpleNeuralStrategy.class);
    }

    /**
     * Register a strategy by class.
     */
    public static void register(String name, Class<? extends BaseStrategy> strategyClass) {
        STRATEGIES.put(name, () -> {
            try {
                return strategyClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create strategy: " + name, e);
            }
        });
    }

    /**
     * Register a strategy with a custom supplier (for dynamically loaded strategies).
     */
    public static void registerStrategy(String name, Supplier<BaseStrategy> supplier) {
        STRATEGIES.put(name, supplier);
    }

    /**
     * Unregister a strategy.
     */
    public static void unregister(String name) {
        STRATEGIES.remove(name);
    }

    /**
     * Get all registered strategy names.
     */
    public static List<String> getStrategyNames() {
        return new ArrayList<>(STRATEGIES.keySet());
    }

    /**
     * Create a new instance of a strategy by name.
     */
    public static BaseStrategy createStrategy(String name) {
        Supplier<BaseStrategy> supplier = STRATEGIES.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("Unknown strategy: " + name);
        }
        return supplier.get();
    }

    /**
     * Create a strategy with parameters.
     */
    public static BaseStrategy createStrategy(String name, Map<String, Object> parameters) {
        BaseStrategy strategy = createStrategy(name);
        strategy.setParameters(parameters);
        return strategy;
    }

    /**
     * Check if a strategy is registered.
     */
    public static boolean isRegistered(String name) {
        return STRATEGIES.containsKey(name);
    }

    /**
     * Get the number of registered strategies.
     */
    public static int count() {
        return STRATEGIES.size();
    }

    /**
     * Clear all dynamically registered strategies, keeping built-ins.
     */
    public static void resetToBuiltins() {
        STRATEGIES.clear();
        register("SMA Crossover", SmaStrategy.class);
        register("RSI Mean Reversion", RsiStrategy.class);
        register("Donchian Breakout", BreakoutStrategy.class);
        register("MACD Signal", MacdStrategy.class);
        register("Neural Network (ML)", SimpleNeuralStrategy.class);
    }
}
