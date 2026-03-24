package com.measim.model.contract;

import java.util.Map;

/**
 * A binding agreement between two agents. The foundation for:
 *   - Employment (agent works for employer, receives wages)
 *   - Rental (tenant pays landlord for tile claim access)
 *   - Trade agreements (recurring supply contracts)
 *   - Service subscriptions (ongoing service consumption)
 *   - Partnerships (shared revenue from a business)
 *   - Custom (any GM-evaluated novel arrangement)
 *
 * Contracts are between agents — GM is NOT involved in standard contracts.
 * GM IS involved when:
 *   - Novel contract terms need feasibility evaluation
 *   - A dispute arises that needs adjudication
 *   - The contract creates unusual externalities
 */
public class Contract {

    public enum ContractType {
        EMPLOYMENT,          // partyA employs partyB, pays wages
        RENTAL,              // partyA rents property to partyB, partyB pays rent
        TRADE_AGREEMENT,     // recurring supply: partyA delivers X to partyB at price Y
        SERVICE_SUBSCRIPTION,// partyB subscribes to partyA's service
        PARTNERSHIP,         // shared ownership/revenue split
        CUSTOM               // GM-evaluated novel terms
    }

    public enum Status { ACTIVE, EXPIRED, BREACHED, TERMINATED_BY_A, TERMINATED_BY_B, DISPUTED }

    private final String id;
    private final ContractType type;
    private final String partyAId;     // employer, landlord, seller, service provider
    private final String partyBId;     // employee, tenant, buyer, consumer
    private final double paymentPerTick;  // credits from B to A (wages: A to B)
    private final int startTick;
    private final int durationTicks;      // -1 = indefinite
    private final int terminationNoticeTicks;  // how many ticks notice required to end
    private Status status;
    private final Map<String, Double> terms;  // custom terms (e.g., "revenueShare": 0.3)

    public Contract(String id, ContractType type, String partyAId, String partyBId,
                    double paymentPerTick, int startTick, int durationTicks,
                    int terminationNoticeTicks, Map<String, Double> terms) {
        this.id = id;
        this.type = type;
        this.partyAId = partyAId;
        this.partyBId = partyBId;
        this.paymentPerTick = paymentPerTick;
        this.startTick = startTick;
        this.durationTicks = durationTicks;
        this.terminationNoticeTicks = terminationNoticeTicks;
        this.status = Status.ACTIVE;
        this.terms = terms;
    }

    public boolean isActive() { return status == Status.ACTIVE; }

    public boolean isExpired(int currentTick) {
        return durationTicks > 0 && currentTick >= startTick + durationTicks;
    }

    /**
     * For EMPLOYMENT: payment flows from A (employer) to B (employee).
     * For RENTAL: payment flows from B (tenant) to A (landlord).
     * Direction determined by type.
     */
    public String payerId() {
        return switch (type) {
            case EMPLOYMENT -> partyAId;  // employer pays employee
            case RENTAL, SERVICE_SUBSCRIPTION -> partyBId;  // tenant/subscriber pays
            case TRADE_AGREEMENT -> partyBId;  // buyer pays
            case PARTNERSHIP -> null;  // complex — handled by terms
            case CUSTOM -> partyBId;  // default: B pays A
        };
    }

    public String payeeId() {
        return switch (type) {
            case EMPLOYMENT -> partyBId;
            case RENTAL, SERVICE_SUBSCRIPTION -> partyAId;
            case TRADE_AGREEMENT -> partyAId;
            case PARTNERSHIP -> null;
            case CUSTOM -> partyAId;
        };
    }

    public void terminate(String initiatorId) {
        status = initiatorId.equals(partyAId) ? Status.TERMINATED_BY_A : Status.TERMINATED_BY_B;
    }

    public void breach() { status = Status.BREACHED; }
    public void dispute() { status = Status.DISPUTED; }
    public void expire() { status = Status.EXPIRED; }

    public String id() { return id; }
    public ContractType type() { return type; }
    public String partyAId() { return partyAId; }
    public String partyBId() { return partyBId; }
    public double paymentPerTick() { return paymentPerTick; }
    public int startTick() { return startTick; }
    public int durationTicks() { return durationTicks; }
    public int terminationNoticeTicks() { return terminationNoticeTicks; }
    public Status status() { return status; }
    public Map<String, Double> terms() { return terms; }
    public double getTerm(String key, double defaultValue) { return terms.getOrDefault(key, defaultValue); }
}
