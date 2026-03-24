package com.measim.service.economy;

import com.measim.model.config.SimulationConfig;
import com.measim.model.economy.OrderBook;
import com.measim.model.economy.Transaction;
import com.measim.model.scoring.ModifierSet;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class CreditFlowServiceImpl implements CreditFlowService {

    private double ubiPool = 0;
    private double environmentalRemediationFund = 0;
    private double commonsInvestmentFund = 0;
    private final double baseTransactionTax;

    @Inject
    public CreditFlowServiceImpl(SimulationConfig config) {
        this.baseTransactionTax = config.baseTransactionTax();
    }

    @Override
    public Transaction applyModifiers(OrderBook.MatchedTrade trade, ModifierSet sellerModifiers,
                                      boolean measEnabled, int currentTick) {
        double baseValue = trade.baseValue();
        if (!measEnabled) {
            return new Transaction(UUID.randomUUID().toString(), trade.buyerId(), trade.sellerId(),
                    trade.itemType(), trade.quantity(), baseValue, baseValue, 0, 0, 0, 0, currentTick);
        }

        double efMod = sellerModifiers.environmentalFootprint();
        double ccMod = sellerModifiers.commonsContribution();
        double rcMod = sellerModifiers.resourceConcentration();
        double ldRate = sellerModifiers.laborDisplacementRate();

        double netCredits = baseValue * efMod * ccMod * rcMod * (1.0 - ldRate);
        double taxAmount = baseValue * baseTransactionTax;
        netCredits -= taxAmount;

        double efDelta = baseValue * (1.0 - efMod);
        double ccDelta = baseValue * (1.0 - ccMod);
        double rcDelta = baseValue * (1.0 - rcMod);
        double ldDiversion = baseValue * ldRate;

        environmentalRemediationFund += Math.max(0, efDelta);
        commonsInvestmentFund += Math.max(0, ccDelta);
        ubiPool += Math.max(0, rcDelta) + ldDiversion + taxAmount;

        return new Transaction(UUID.randomUUID().toString(), trade.buyerId(), trade.sellerId(),
                trade.itemType(), trade.quantity(), baseValue, Math.max(0, netCredits),
                efDelta, ccDelta, rcDelta, ldDiversion, currentTick);
    }

    @Override
    public double distributeUbi(int eligiblePopulation) {
        if (eligiblePopulation <= 0) return 0;
        double perCapita = ubiPool / eligiblePopulation;
        ubiPool = 0;
        return perCapita;
    }

    @Override
    public void addPublicRevenue(double amount, String source) {
        if (amount > 0) ubiPool += amount;
    }

    @Override public double ubiPool() { return ubiPool; }
    @Override public double environmentalRemediationFund() { return environmentalRemediationFund; }
    @Override public double commonsInvestmentFund() { return commonsInvestmentFund; }
}
