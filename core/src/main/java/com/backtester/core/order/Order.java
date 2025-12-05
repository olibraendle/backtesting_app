package com.backtester.core.order;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a trading order.
 */
public class Order {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    private final long id;
    private final String symbol;
    private final OrderType type;
    private final OrderAction action;
    private final double quantity;
    private final double limitPrice;  // For LIMIT and STOP_LIMIT orders
    private final double stopPrice;   // For STOP and STOP_LIMIT orders
    private final long createdTime;
    private final int createdBarIndex;

    private OrderStatus status;
    private double filledPrice;
    private long filledTime;
    private int filledBarIndex;
    private String rejectReason;

    private Order(Builder builder) {
        this.id = ID_GENERATOR.incrementAndGet();
        this.symbol = builder.symbol;
        this.type = builder.type;
        this.action = builder.action;
        this.quantity = builder.quantity;
        this.limitPrice = builder.limitPrice;
        this.stopPrice = builder.stopPrice;
        this.createdTime = builder.createdTime;
        this.createdBarIndex = builder.createdBarIndex;
        this.status = OrderStatus.PENDING;
    }

    // ===== Factory Methods =====

    public static Order marketBuy(String symbol, double quantity, long time, int barIndex) {
        return new Builder()
                .symbol(symbol)
                .type(OrderType.MARKET)
                .action(OrderAction.BUY)
                .quantity(quantity)
                .createdTime(time)
                .createdBarIndex(barIndex)
                .build();
    }

    public static Order marketSell(String symbol, double quantity, long time, int barIndex) {
        return new Builder()
                .symbol(symbol)
                .type(OrderType.MARKET)
                .action(OrderAction.SELL)
                .quantity(quantity)
                .createdTime(time)
                .createdBarIndex(barIndex)
                .build();
    }

    public static Order limitBuy(String symbol, double quantity, double limitPrice, long time, int barIndex) {
        return new Builder()
                .symbol(symbol)
                .type(OrderType.LIMIT)
                .action(OrderAction.BUY)
                .quantity(quantity)
                .limitPrice(limitPrice)
                .createdTime(time)
                .createdBarIndex(barIndex)
                .build();
    }

    public static Order limitSell(String symbol, double quantity, double limitPrice, long time, int barIndex) {
        return new Builder()
                .symbol(symbol)
                .type(OrderType.LIMIT)
                .action(OrderAction.SELL)
                .quantity(quantity)
                .limitPrice(limitPrice)
                .createdTime(time)
                .createdBarIndex(barIndex)
                .build();
    }

    public static Order stopBuy(String symbol, double quantity, double stopPrice, long time, int barIndex) {
        return new Builder()
                .symbol(symbol)
                .type(OrderType.STOP)
                .action(OrderAction.BUY)
                .quantity(quantity)
                .stopPrice(stopPrice)
                .createdTime(time)
                .createdBarIndex(barIndex)
                .build();
    }

    public static Order stopSell(String symbol, double quantity, double stopPrice, long time, int barIndex) {
        return new Builder()
                .symbol(symbol)
                .type(OrderType.STOP)
                .action(OrderAction.SELL)
                .quantity(quantity)
                .stopPrice(stopPrice)
                .createdTime(time)
                .createdBarIndex(barIndex)
                .build();
    }

    // ===== Status Updates =====

    public void fill(double price, long time, int barIndex) {
        this.status = OrderStatus.FILLED;
        this.filledPrice = price;
        this.filledTime = time;
        this.filledBarIndex = barIndex;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public void reject(String reason) {
        this.status = OrderStatus.REJECTED;
        this.rejectReason = reason;
    }

    public void expire() {
        this.status = OrderStatus.EXPIRED;
    }

    // ===== Getters =====

    public long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderType getType() {
        return type;
    }

    public OrderAction getAction() {
        return action;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getLimitPrice() {
        return limitPrice;
    }

    public double getStopPrice() {
        return stopPrice;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public int getCreatedBarIndex() {
        return createdBarIndex;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public double getFilledPrice() {
        return filledPrice;
    }

    public long getFilledTime() {
        return filledTime;
    }

    public int getFilledBarIndex() {
        return filledBarIndex;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    public boolean isFilled() {
        return status == OrderStatus.FILLED;
    }

    public boolean isBuy() {
        return action == OrderAction.BUY;
    }

    public boolean isSell() {
        return action == OrderAction.SELL;
    }

    public boolean isMarket() {
        return type == OrderType.MARKET;
    }

    public boolean isLimit() {
        return type == OrderType.LIMIT;
    }

    public boolean isStop() {
        return type == OrderType.STOP;
    }

    // ===== Builder =====

    public static class Builder {
        private String symbol;
        private OrderType type;
        private OrderAction action;
        private double quantity;
        private double limitPrice;
        private double stopPrice;
        private long createdTime;
        private int createdBarIndex;

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder type(OrderType type) {
            this.type = type;
            return this;
        }

        public Builder action(OrderAction action) {
            this.action = action;
            return this;
        }

        public Builder quantity(double quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder limitPrice(double limitPrice) {
            this.limitPrice = limitPrice;
            return this;
        }

        public Builder stopPrice(double stopPrice) {
            this.stopPrice = stopPrice;
            return this;
        }

        public Builder createdTime(long createdTime) {
            this.createdTime = createdTime;
            return this;
        }

        public Builder createdBarIndex(int createdBarIndex) {
            this.createdBarIndex = createdBarIndex;
            return this;
        }

        public Order build() {
            return new Order(this);
        }
    }

    @Override
    public String toString() {
        return String.format("Order[#%d %s %s %.4f %s @ %s, Status=%s]",
                id, action, type, quantity, symbol,
                type == OrderType.MARKET ? "MARKET" :
                        type == OrderType.LIMIT ? String.format("%.5f", limitPrice) :
                                String.format("STOP %.5f", stopPrice),
                status);
    }
}
