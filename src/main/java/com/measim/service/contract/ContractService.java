package com.measim.service.contract;

import com.measim.model.contract.Contract;

import java.util.List;
import java.util.Optional;

public interface ContractService {
    /**
     * Create a contract between two agents. Standard contracts don't need GM.
     */
    Contract createContract(Contract.ContractType type, String partyAId, String partyBId,
                             double paymentPerTick, int durationTicks, int currentTick,
                             java.util.Map<String, Double> terms);

    /**
     * Process all active contracts: payments, expiration, breach detection.
     */
    void processContracts(int currentTick);

    /**
     * Terminate a contract. May trigger breach if notice period not met.
     */
    void terminateContract(String contractId, String initiatorId, int currentTick);

    // Employment-specific
    List<Contract> getEmployeesOf(String employerId);
    Optional<Contract> getEmploymentOf(String employeeId);
    int getHumanEmployeeCount(String employerId);

    // Queries
    List<Contract> getAgentContracts(String agentId);
}
