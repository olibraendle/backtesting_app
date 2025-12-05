package com.backtester.core.order;

/**
 * Represents an order fill (execution).
 */
public record Fill(
        long orderId,
        String symbol,
        OrderAction action,
        double quantity,
        double price,
        double commission,
        double slippage,
        long timestamp,
        int barIndex
) {
    /**
     * Total cost of the fill including commission.
     */
    public double totalCost() {
        return price * quantity + commission;
    }

    /**
     * Total proceeds from the fill minus commission.
     */
    public double netProceeds() {
        return price * quantity - commission;
    }

    @Override
    public String toString() {
        return String.format("Fill[Order#%d %s %.4f @ %.5f, Commission=%.2f]",
                orderId, action, quantity, price, commission);
    }
}
