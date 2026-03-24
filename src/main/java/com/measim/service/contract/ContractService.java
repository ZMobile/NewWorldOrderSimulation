package com.measim.service.contract;

import com.measim.model.contract.Contract;

import java.util.List;
import java.util.Optional;

public interface ContractService {
    Contract createContract(Contract.ContractType type, String partyAId, String partyBId,
                             double paymentPerTick, int durationTicks, int currentTick,
                             java.util.Map<String, Double> terms);
    void processContracts(int currentTick);
    void terminateContract(String contractId, String initiatorId, int currentTick);

    // Work relation queries
    List<Contract> getWorkRelationsOf(String hirerId);
    Optional<Contract> getWorkRelationFor(String workerId);

    /**
     * Weighted human labor count for LD axis.
     * Sums laborWeight() across all active work relations for a hirer.
     * A full-time worker = 1.0, a half-time contractor = 0.5, a gig worker = varies.
     */
    double getWeightedLaborCount(String hirerId);

    List<Contract> getAgentContracts(String agentId);
}
