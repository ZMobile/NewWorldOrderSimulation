package com.measim.dao;

import com.measim.model.contract.Contract;

import java.util.List;
import java.util.Optional;

public interface ContractDao {
    void addContract(Contract contract);
    Optional<Contract> getContract(String id);
    List<Contract> getActiveContracts();
    List<Contract> getContractsForAgent(String agentId);
    List<Contract> getContractsByType(Contract.ContractType type);
    List<Contract> getWorkRelationsForHirer(String hirerId);
    List<Contract> getWorkRelationsForWorker(String workerId);
}
