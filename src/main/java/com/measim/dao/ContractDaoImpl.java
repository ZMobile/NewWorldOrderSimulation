package com.measim.dao;

import com.measim.model.contract.Contract;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class ContractDaoImpl implements ContractDao {

    private final Map<String, Contract> contracts = new LinkedHashMap<>();

    @Override public void addContract(Contract c) { contracts.put(c.id(), c); }
    @Override public Optional<Contract> getContract(String id) { return Optional.ofNullable(contracts.get(id)); }
    @Override public List<Contract> getActiveContracts() {
        return contracts.values().stream().filter(Contract::isActive).toList();
    }
    @Override public List<Contract> getContractsForAgent(String agentId) {
        return contracts.values().stream()
                .filter(c -> c.partyAId().equals(agentId) || c.partyBId().equals(agentId)).toList();
    }
    @Override public List<Contract> getContractsByType(Contract.ContractType type) {
        return contracts.values().stream().filter(c -> c.isActive() && c.type() == type).toList();
    }
    @Override public List<Contract> getEmploymentContractsForEmployer(String employerId) {
        return contracts.values().stream()
                .filter(c -> c.isActive() && c.type() == Contract.ContractType.EMPLOYMENT && c.partyAId().equals(employerId)).toList();
    }
    @Override public List<Contract> getEmploymentContractsForEmployee(String employeeId) {
        return contracts.values().stream()
                .filter(c -> c.isActive() && c.type() == Contract.ContractType.EMPLOYMENT && c.partyBId().equals(employeeId)).toList();
    }
}
