package com.backtester.core.portfolio;

/**
 * Represents a completed round-trip trade (entry + exit).
 */
public record Trade(
        String symbol,
        Side side,
        long entryTime,
        long exitTime,
        double entryPrice,
        double exitPrice,
        double quantity,
        double grossPnl,
        double commission,
        double slippage,
        double netPnl,
        int barsHeld,
        int entryBarIndex,
        int exitBarIndex,
        double maxFavorableExcursion,
        double maxAdverseExcursion
) {
    /**
     * Calculate return percentage.
     */
    public double returnPercent() {
        if (entryPrice == 0) return 0;
        return (netPnl / (entryPrice * quantity)) * 100;
    }

    /**
     * Calculate gross return percentage (before costs).
     */
    public double grossReturnPercent() {
        if (entryPrice == 0) return 0;
        return (grossPnl / (entryPrice * quantity)) * 100;
    }

    /**
     * Is this a winning trade?
     */
    public boolean isWin() {
        return netPnl > 0;
    }

    /**
     * Is this a losing trade?
     */
    public boolean isLoss() {
        return netPnl < 0;
    }

    /**
     * Is this a breakeven trade?
     */
    public boolean isBreakeven() {
        return Math.abs(netPnl) < 0.01;
    }

    /**
     * Total trading costs.
     */
    public double totalCosts() {
        return commission + slippage;
    }

    /**
     * R-multiple: net P&L divided by initial risk (approximated by MAE).
     */
    public double rMultiple() {
        if (maxAdverseExcursion == 0) return 0;
        return netPnl / Math.abs(maxAdverseExcursion);
    }

    /**
     * Entry value (position size at entry).
     */
    public double entryValue() {
        return entryPrice * quantity;
    }

    /**
     * Exit value (position size at exit).
     */
    public double exitValue() {
        return exitPrice * quantity;
    }

    /**
     * Trade duration in milliseconds.
     */
    public long durationMs() {
        return exitTime - entryTime;
    }

    /**
     * Builder for Trade creation.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String symbol;
        private Side side;
        private long entryTime;
        private long exitTime;
        private double entryPrice;
        private double exitPrice;
        private double quantity;
        private double grossPnl;
        private double commission;
        private double slippage;
        private double netPnl;
        private int barsHeld;
        private int entryBarIndex;
        private int exitBarIndex;
        private double maxFavorableExcursion;
        private double maxAdverseExcursion;

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder side(Side side) {
            this.side = side;
            return this;
        }

        public Builder entryTime(long entryTime) {
            this.entryTime = entryTime;
            return this;
        }

        public Builder exitTime(long exitTime) {
            this.exitTime = exitTime;
            return this;
        }

        public Builder entryPrice(double entryPrice) {
            this.entryPrice = entryPrice;
            return this;
        }

        public Builder exitPrice(double exitPrice) {
            this.exitPrice = exitPrice;
            return this;
        }

        public Builder quantity(double quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder grossPnl(double grossPnl) {
            this.grossPnl = grossPnl;
            return this;
        }

        public Builder commission(double commission) {
            this.commission = commission;
            return this;
        }

        public Builder slippage(double slippage) {
            this.slippage = slippage;
            return this;
        }

        public Builder netPnl(double netPnl) {
            this.netPnl = netPnl;
            return this;
        }

        public Builder barsHeld(int barsHeld) {
            this.barsHeld = barsHeld;
            return this;
        }

        public Builder entryBarIndex(int entryBarIndex) {
            this.entryBarIndex = entryBarIndex;
            return this;
        }

        public Builder exitBarIndex(int exitBarIndex) {
            this.exitBarIndex = exitBarIndex;
            return this;
        }

        public Builder maxFavorableExcursion(double mfe) {
            this.maxFavorableExcursion = mfe;
            return this;
        }

        public Builder maxAdverseExcursion(double mae) {
            this.maxAdverseExcursion = mae;
            return this;
        }

        public Trade build() {
            return new Trade(symbol, side, entryTime, exitTime, entryPrice, exitPrice,
                    quantity, grossPnl, commission, slippage, netPnl, barsHeld,
                    entryBarIndex, exitBarIndex, maxFavorableExcursion, maxAdverseExcursion);
        }
    }

    @Override
    public String toString() {
        return String.format("Trade[%s %s %.4f, Entry=%.5f Exit=%.5f, PnL=%.2f (%.2f%%), %d bars]",
                side, symbol, quantity, entryPrice, exitPrice, netPnl, returnPercent(), barsHeld);
    }
}
