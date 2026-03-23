package com.measim.service.economy;

import com.measim.model.economy.OrderBook;
import com.measim.model.economy.Transaction;
import com.measim.model.scoring.ModifierSet;

public interface CreditFlowService {
    Transaction applyModifiers(OrderBook.MatchedTrade trade, ModifierSet sellerModifiers,
                               boolean measEnabled, int currentTick);
    double distributeUbi(int eligiblePopulation);
    double ubiPool();
    double environmentalRemediationFund();
    double commonsInvestmentFund();
}
