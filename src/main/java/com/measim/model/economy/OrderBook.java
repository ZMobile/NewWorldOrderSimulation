package com.measim.model.economy;

import java.util.*;

public class OrderBook {

    private final ItemType itemType;
    private final PriorityQueue<Order> buyOrders;
    private final PriorityQueue<Order> sellOrders;

    public OrderBook(ItemType itemType) {
        this.itemType = itemType;
        this.buyOrders = new PriorityQueue<>(Comparator.comparingDouble(Order::pricePerUnit).reversed());
        this.sellOrders = new PriorityQueue<>(Comparator.comparingDouble(Order::pricePerUnit));
    }

    public void submit(Order order) {
        if (order.side() == Order.OrderSide.BUY) buyOrders.add(order);
        else sellOrders.add(order);
    }

    public List<MatchedTrade> match() {
        List<MatchedTrade> trades = new ArrayList<>();
        while (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
            Order buy = buyOrders.peek();
            Order sell = sellOrders.peek();
            if (buy.pricePerUnit() < sell.pricePerUnit()) break;

            buyOrders.poll();
            sellOrders.poll();
            int tradedQty = Math.min(buy.quantity(), sell.quantity());
            double tradePrice = (buy.pricePerUnit() + sell.pricePerUnit()) / 2.0;
            trades.add(new MatchedTrade(buy.agentId(), sell.agentId(), itemType, tradedQty, tradePrice));

            if (buy.quantity() > tradedQty)
                buyOrders.add(new Order(buy.agentId(), buy.itemType(),
                        buy.quantity() - tradedQty, buy.pricePerUnit(), Order.OrderSide.BUY, buy.tickPlaced()));
            if (sell.quantity() > tradedQty)
                sellOrders.add(new Order(sell.agentId(), sell.itemType(),
                        sell.quantity() - tradedQty, sell.pricePerUnit(), Order.OrderSide.SELL, sell.tickPlaced()));
        }
        return trades;
    }

    public double lastPrice() {
        if (buyOrders.isEmpty() || sellOrders.isEmpty()) return 0;
        return (buyOrders.peek().pricePerUnit() + sellOrders.peek().pricePerUnit()) / 2.0;
    }

    public ItemType itemType() { return itemType; }
    public int buyDepth() { return buyOrders.size(); }
    public int sellDepth() { return sellOrders.size(); }

    public record MatchedTrade(String buyerId, String sellerId, ItemType itemType,
                               int quantity, double pricePerUnit) {
        public double baseValue() { return quantity * pricePerUnit; }
    }
}
