package com.backtester.core.engine;

import com.backtester.core.market.CommissionModel;
import com.backtester.core.market.SlippageModel;
import com.backtester.core.market.SpreadModel;

/**
 * Configuration for backtesting parameters.
 */
public record BacktestConfig(
        double initialCapital,
        CommissionModel commissionModel,
        SpreadModel spreadModel,
        SlippageModel slippageModel,
        boolean allowShorts,
        double maxPositionSizePercent,  // Max % of equity per position
        int warmupBars,                 // Bars to skip for indicator warmup
        boolean integerQuantityOnly     // Force integer quantities (for futures contracts)
) {
    /**
     * Default configuration with $100,000 starting capital.
     */
    public static BacktestConfig defaultConfig() {
        return new BacktestConfig(
                100_000.0,
                CommissionModel.percentage(0.1),
                SpreadModel.percentage(0.01),
                SlippageModel.fixedPercent(0.05),
                true,
                100.0,  // 100% of equity (no limit)
                0,      // No warmup
                false   // Allow fractional quantities
        );
    }

    /**
     * Zero-cost configuration for testing strategy logic.
     */
    public static BacktestConfig zeroCost() {
        return new BacktestConfig(
                100_000.0,
                CommissionModel.zero(),
                SpreadModel.zero(),
                SlippageModel.zero(),
                true,
                100.0,
                0,
                false   // Allow fractional quantities
        );
    }

    /**
     * Builder for creating custom configurations.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double initialCapital = 100_000.0;
        private CommissionModel commissionModel = CommissionModel.percentage(0.1);
        private SpreadModel spreadModel = SpreadModel.percentage(0.01);
        private SlippageModel slippageModel = SlippageModel.fixedPercent(0.05);
        private boolean allowShorts = true;
        private double maxPositionSizePercent = 100.0;
        private int warmupBars = 0;
        private boolean integerQuantityOnly = false;

        public Builder initialCapital(double initialCapital) {
            this.initialCapital = initialCapital;
            return this;
        }

        public Builder commissionModel(CommissionModel commissionModel) {
            this.commissionModel = commissionModel;
            return this;
        }

        public Builder commissionPercent(double percent) {
            this.commissionModel = CommissionModel.percentage(percent);
            return this;
        }

        public Builder spreadModel(SpreadModel spreadModel) {
            this.spreadModel = spreadModel;
            return this;
        }

        public Builder spreadPercent(double percent) {
            this.spreadModel = SpreadModel.percentage(percent);
            return this;
        }

        public Builder slippageModel(SlippageModel slippageModel) {
            this.slippageModel = slippageModel;
            return this;
        }

        public Builder slippagePercent(double percent) {
            this.slippageModel = SlippageModel.fixedPercent(percent);
            return this;
        }

        public Builder allowShorts(boolean allowShorts) {
            this.allowShorts = allowShorts;
            return this;
        }

        public Builder maxPositionSizePercent(double maxPositionSizePercent) {
            this.maxPositionSizePercent = maxPositionSizePercent;
            return this;
        }

        public Builder warmupBars(int warmupBars) {
            this.warmupBars = warmupBars;
            return this;
        }

        /**
         * Set to true to enforce integer quantities only (for futures contracts).
         */
        public Builder integerQuantityOnly(boolean integerQuantityOnly) {
            this.integerQuantityOnly = integerQuantityOnly;
            return this;
        }

        public BacktestConfig build() {
            return new BacktestConfig(
                    initialCapital,
                    commissionModel,
                    spreadModel,
                    slippageModel,
                    allowShorts,
                    maxPositionSizePercent,
                    warmupBars,
                    integerQuantityOnly
            );
        }
    }

    @Override
    public String toString() {
        return String.format(
                "BacktestConfig[Capital=$%.0f, Commission=%s, Spread=%s, Slippage=%s, Shorts=%s]",
                initialCapital, commissionModel, spreadModel, slippageModel, allowShorts ? "Yes" : "No"
        );
    }
}
