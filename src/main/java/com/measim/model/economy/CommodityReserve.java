package com.measim.model.economy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The commodity reserve backing the credit system.
 * Holds physical resources that give credits their floor value.
 *
 * Credits are NOT directly exchangeable for reserve commodities by agents.
 * The reserve exists to anchor confidence: the system can't create credits
 * beyond what reserves support. The GM manages reserve trades to maintain
 * the ratio.
 *
 * All operations are numerically tracked and auditable.
 */
public class CommodityReserve {

    private final ConcurrentHashMap<String, Double> holdings;  // commodity → quantity
    private final ConcurrentHashMap<String, Double> valuations; // commodity → credits per unit (GM-set)
    private final List<ReserveTransaction> transactionLog;
    private double minimumRatio;  // minimum reserve value / total credits (governance parameter)

    public CommodityReserve(double minimumRatio) {
        this.holdings = new ConcurrentHashMap<>();
        this.valuations = new ConcurrentHashMap<>();
        this.transactionLog = Collections.synchronizedList(new ArrayList<>());
        this.minimumRatio = minimumRatio;
    }

    public void setValuation(String commodity, double creditsPerUnit) {
        valuations.put(commodity, creditsPerUnit);
    }

    public double getValuation(String commodity) {
        return valuations.getOrDefault(commodity, 0.0);
    }

    public double getHolding(String commodity) {
        return holdings.getOrDefault(commodity, 0.0);
    }

    /**
     * Add commodities to the reserve (GM buying resources to back credits).
     */
    public void deposit(String commodity, double quantity, double creditCost, int tick, String reason) {
        holdings.merge(commodity, quantity, Double::sum);
        transactionLog.add(new ReserveTransaction(
                tick, "DEPOSIT", commodity, quantity, creditCost, reason, totalValue()));
    }

    /**
     * Remove commodities from the reserve (GM selling to manage ratio).
     */
    public boolean withdraw(String commodity, double quantity, double creditGain, int tick, String reason) {
        double current = holdings.getOrDefault(commodity, 0.0);
        if (current < quantity) return false;
        holdings.put(commodity, current - quantity);
        transactionLog.add(new ReserveTransaction(
                tick, "WITHDRAW", commodity, quantity, creditGain, reason, totalValue()));
        return true;
    }

    /**
     * Total reserve value in credits (sum of all holdings × their valuations).
     */
    public double totalValue() {
        double total = 0;
        for (var entry : holdings.entrySet()) {
            total += entry.getValue() * valuations.getOrDefault(entry.getKey(), 0.0);
        }
        return total;
    }

    /**
     * Current reserve ratio = total reserve value / total credits in circulation.
     */
    public double currentRatio(double totalCreditsInCirculation) {
        if (totalCreditsInCirculation <= 0) return 1.0;
        return totalValue() / totalCreditsInCirculation;
    }

    /**
     * Can the system create this many new credits without violating the reserve ratio?
     */
    public boolean canCreateCredits(double amount, double currentTotalCredits) {
        double newTotal = currentTotalCredits + amount;
        return currentRatio(newTotal) >= minimumRatio;
    }

    public double minimumRatio() { return minimumRatio; }
    public void setMinimumRatio(double ratio) { this.minimumRatio = ratio; }
    public ConcurrentHashMap<String, Double> holdings() { return holdings; }
    public ConcurrentHashMap<String, Double> valuations() { return valuations; }
    public List<ReserveTransaction> transactionLog() { return Collections.unmodifiableList(transactionLog); }

    public record ReserveTransaction(
            int tick, String type, String commodity, double quantity,
            double creditAmount, String reason, double reserveValueAfter
    ) {}
}
