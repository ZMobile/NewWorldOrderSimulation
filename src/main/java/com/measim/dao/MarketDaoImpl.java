package com.measim.dao;

import com.measim.model.economy.*;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class MarketDaoImpl implements MarketDao {

    private final Map<String, MarketDataImpl> markets = new HashMap<>();

    @Override
    public MarketData getOrCreateMarket(String settlementId) {
        return markets.computeIfAbsent(settlementId, MarketDataImpl::new);
    }

    @Override
    public Collection<MarketData> getAllMarkets() {
        return Collections.unmodifiableCollection(markets.values());
    }

    @Override
    public void recordTransaction(String marketId, Transaction transaction) {
        MarketDataImpl market = markets.get(marketId);
        if (market != null) market.recordTransaction(transaction);
    }

    @Override
    public List<Transaction> getTransactionHistory(String marketId) {
        MarketDataImpl market = markets.get(marketId);
        return market != null ? market.transactionHistory() : List.of();
    }

    private static class MarketDataImpl implements MarketData {
        private final String settlementId;
        private final Map<ItemType, OrderBook> orderBooks = new HashMap<>();
        private final List<Transaction> transactions = new ArrayList<>();
        private final Map<ItemType, List<Double>> priceHistory = new HashMap<>();

        MarketDataImpl(String settlementId) { this.settlementId = settlementId; }

        @Override public String settlementId() { return settlementId; }

        @Override
        public void submitOrder(Order order) {
            orderBooks.computeIfAbsent(order.itemType(), OrderBook::new).submit(order);
        }

        @Override
        public List<OrderBook.MatchedTrade> resolveAll() {
            List<OrderBook.MatchedTrade> allTrades = new ArrayList<>();
            for (OrderBook book : orderBooks.values()) {
                var trades = book.match();
                allTrades.addAll(trades);
                for (var trade : trades)
                    priceHistory.computeIfAbsent(trade.itemType(), k -> new ArrayList<>())
                            .add(trade.pricePerUnit());
            }
            return allTrades;
        }

        @Override
        public double getLastPrice(ItemType itemType) {
            List<Double> h = priceHistory.get(itemType);
            return (h == null || h.isEmpty()) ? 0 : h.getLast();
        }

        @Override
        public double getAveragePrice(ItemType itemType, int lastN) {
            List<Double> h = priceHistory.get(itemType);
            if (h == null || h.isEmpty()) return 0;
            int start = Math.max(0, h.size() - lastN);
            return h.subList(start, h.size()).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        void recordTransaction(Transaction tx) { transactions.add(tx); }

        @Override
        public List<Transaction> transactionHistory() { return Collections.unmodifiableList(transactions); }
    }
}
