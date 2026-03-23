package com.measim.dao;

import com.measim.model.economy.*;

import java.util.Collection;
import java.util.List;

public interface MarketDao {
    MarketData getOrCreateMarket(String settlementId);
    Collection<MarketData> getAllMarkets();
    void recordTransaction(String marketId, Transaction transaction);
    List<Transaction> getTransactionHistory(String marketId);

    interface MarketData {
        String settlementId();
        void submitOrder(Order order);
        List<OrderBook.MatchedTrade> resolveAll();
        double getLastPrice(ItemType itemType);
        double getAveragePrice(ItemType itemType, int lastN);
        List<Transaction> transactionHistory();
    }
}
