package com.backtester;

/**
 * Launcher class for the Backtester application.
 *
 * This separate launcher is needed because JavaFX requires the main class
 * to NOT extend Application when running from a JAR without module-path.
 * This class simply delegates to BacktesterApp.main().
 */
public class Launcher {

    public static void main(String[] args) {
        BacktesterApp.main(args);
    }
}
