package com.backtester.core.order;

import com.backtester.common.model.Bar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages pending orders and executes them based on market conditions.
 */
public class OrderManager {
    private final List<Order> pendingOrders;
    private final List<Order> allOrders;

    public OrderManager() {
        this.pendingOrders = new ArrayList<>();
        this.allOrders = new ArrayList<>();
    }

    /**
     * Submit a new order.
     */
    public void submit(Order order) {
        allOrders.add(order);
        if (order.isPending()) {
            pendingOrders.add(order);
        }
    }

    /**
     * Process orders against the current bar.
     * Returns list of fills.
     */
    public List<Fill> processOrders(Bar bar, double commission, double slippagePercent) {
        List<Fill> fills = new ArrayList<>();
        List<Order> toRemove = new ArrayList<>();

        for (Order order : pendingOrders) {
            Fill fill = tryFill(order, bar, commission, slippagePercent);
            if (fill != null) {
                fills.add(fill);
                toRemove.add(order);
            }
        }

        pendingOrders.removeAll(toRemove);
        return fills;
    }

    /**
     * Try to fill an order against the current bar.
     */
    private Fill tryFill(Order order, Bar bar, double commission, double slippagePercent) {
        Double fillPrice = getFillPrice(order, bar);
        if (fillPrice == null) {
            return null;
        }

        // Apply slippage
        double slippage = fillPrice * slippagePercent;
        double adjustedPrice = order.isBuy()
                ? fillPrice + slippage
                : fillPrice - slippage;

        // Mark order as filled
        order.fill(adjustedPrice, bar.timestamp(), bar.index());

        return new Fill(
                order.getId(),
                order.getSymbol(),
                order.getAction(),
                order.getQuantity(),
                adjustedPrice,
                commission,
                slippage * order.getQuantity(),
                bar.timestamp(),
                bar.index()
        );
    }

    /**
     * Determine fill price for an order given current bar.
     * Returns null if order cannot be filled.
     */
    private Double getFillPrice(Order order, Bar bar) {
        return switch (order.getType()) {
            case MARKET -> {
                // Market orders fill at open of next bar (we're processing at close)
                // In backtesting, we use close as approximation
                yield bar.close();
            }
            case LIMIT -> {
                if (order.isBuy()) {
                    // Buy limit fills if price goes below limit
                    if (bar.low() <= order.getLimitPrice()) {
                        yield Math.min(order.getLimitPrice(), bar.open());
                    }
                } else {
                    // Sell limit fills if price goes above limit
                    if (bar.high() >= order.getLimitPrice()) {
                        yield Math.max(order.getLimitPrice(), bar.open());
                    }
                }
                yield null;
            }
            case STOP -> {
                if (order.isBuy()) {
                    // Buy stop fills if price goes above stop
                    if (bar.high() >= order.getStopPrice()) {
                        yield Math.max(order.getStopPrice(), bar.open());
                    }
                } else {
                    // Sell stop fills if price goes below stop
                    if (bar.low() <= order.getStopPrice()) {
                        yield Math.min(order.getStopPrice(), bar.open());
                    }
                }
                yield null;
            }
            case STOP_LIMIT -> {
                // Stop-limit: stop triggers, then limit order placed
                // Simplified: just check both conditions
                if (order.isBuy()) {
                    if (bar.high() >= order.getStopPrice() && bar.low() <= order.getLimitPrice()) {
                        yield order.getLimitPrice();
                    }
                } else {
                    if (bar.low() <= order.getStopPrice() && bar.high() >= order.getLimitPrice()) {
                        yield order.getLimitPrice();
                    }
                }
                yield null;
            }
        };
    }

    /**
     * Cancel all pending orders.
     */
    public void cancelAllPending() {
        for (Order order : pendingOrders) {
            order.cancel();
        }
        pendingOrders.clear();
    }

    /**
     * Cancel a specific order by ID.
     */
    public boolean cancelOrder(long orderId) {
        for (Order order : pendingOrders) {
            if (order.getId() == orderId) {
                order.cancel();
                pendingOrders.remove(order);
                return true;
            }
        }
        return false;
    }

    /**
     * Get count of pending orders.
     */
    public int getPendingCount() {
        return pendingOrders.size();
    }

    /**
     * Check if there are pending orders.
     */
    public boolean hasPendingOrders() {
        return !pendingOrders.isEmpty();
    }

    /**
     * Get all pending orders.
     */
    public List<Order> getPendingOrders() {
        return Collections.unmodifiableList(pendingOrders);
    }

    /**
     * Get all orders (historical).
     */
    public List<Order> getAllOrders() {
        return Collections.unmodifiableList(allOrders);
    }

    /**
     * Reset order manager.
     */
    public void reset() {
        pendingOrders.clear();
        allOrders.clear();
    }
}
