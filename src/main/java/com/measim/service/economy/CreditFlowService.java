package com.measim.service.economy;

import com.measim.model.economy.OrderBook;
import com.measim.model.economy.Transaction;
import com.measim.model.scoring.ModifierSet;

public interface CreditFlowService {
    Transaction applyModifiers(OrderBook.MatchedTrade trade, ModifierSet sellerModifiers,
                               boolean measEnabled, int currentTick);
    double distributeUbi(int eligiblePopulation);
    /** Add public revenue to the UBI pool (reserve premiums, property sales, extraction fees). */
    void addPublicRevenue(double amount, String source);
    /** Drain public revenue pool for reserve use (capitalism mode). Returns amount drained. */
    double drainPublicRevenueToReserve();
    double ubiPool();
    double environmentalRemediationFund();
    double commonsInvestmentFund();
}
