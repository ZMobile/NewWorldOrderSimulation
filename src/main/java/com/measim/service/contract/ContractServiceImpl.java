package com.measim.service.contract;

import com.measim.dao.AgentDao;
import com.measim.dao.ContractDao;
import com.measim.model.agent.Agent;
import com.measim.model.agent.EmploymentStatus;
import com.measim.model.agent.MemoryEntry;
import com.measim.model.contract.Contract;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class ContractServiceImpl implements ContractService {

    private final ContractDao contractDao;
    private final AgentDao agentDao;

    @Inject
    public ContractServiceImpl(ContractDao contractDao, AgentDao agentDao) {
        this.contractDao = contractDao;
        this.agentDao = agentDao;
    }

    @Override
    public Contract createContract(Contract.ContractType type, String partyAId, String partyBId,
                                    double paymentPerTick, int durationTicks, int currentTick,
                                    Map<String, Double> terms) {
        String id = "contract_" + UUID.randomUUID().toString().substring(0, 8);
        Contract contract = new Contract(id, type, partyAId, partyBId,
                paymentPerTick, currentTick, durationTicks, 3, terms);
        contractDao.addContract(contract);

        // Update employment status
        if (type == Contract.ContractType.EMPLOYMENT) {
            Agent employee = agentDao.getAgent(partyBId);
            if (employee != null) employee.state().setEmploymentStatus(EmploymentStatus.EMPLOYED);
            Agent employer = agentDao.getAgent(partyAId);
            if (employer != null) employer.state().setEmploymentStatus(EmploymentStatus.BUSINESS_OWNER);
        }

        return contract;
    }

    @Override
    public void processContracts(int currentTick) {
        for (Contract contract : contractDao.getActiveContracts()) {
            // Check expiration
            if (contract.isExpired(currentTick)) {
                contract.expire();
                handleContractEnd(contract);
                continue;
            }

            // Process payment
            String payerId = contract.payerId();
            String payeeId = contract.payeeId();
            if (payerId == null || payeeId == null) continue;

            Agent payer = agentDao.getAgent(payerId);
            Agent payee = agentDao.getAgent(payeeId);
            if (payer == null || payee == null) continue;

            if (payer.state().spendCredits(contract.paymentPerTick())) {
                payee.state().addCredits(contract.paymentPerTick());
                payee.state().addRevenue(contract.paymentPerTick());

                // Track human employees for LD axis
                if (contract.type() == Contract.ContractType.EMPLOYMENT) {
                    Agent employer = agentDao.getAgent(contract.partyAId());
                    if (employer != null) {
                        employer.state().setHumanEmployees(getHumanEmployeeCount(contract.partyAId()));
                    }
                }
            } else {
                // Can't pay — breach
                contract.breach();
                handleContractEnd(contract);
                payer.addMemory(new MemoryEntry(currentTick, "CONTRACT",
                        "Breached contract: couldn't afford payment of " + contract.paymentPerTick(),
                        0.8, contract.payeeId(), 0));
            }
        }
    }

    @Override
    public void terminateContract(String contractId, String initiatorId, int currentTick) {
        contractDao.getContract(contractId).ifPresent(contract -> {
            contract.terminate(initiatorId);
            handleContractEnd(contract);
        });
    }

    @Override
    public List<Contract> getEmployeesOf(String employerId) {
        return contractDao.getEmploymentContractsForEmployer(employerId);
    }

    @Override
    public Optional<Contract> getEmploymentOf(String employeeId) {
        return contractDao.getEmploymentContractsForEmployee(employeeId).stream().findFirst();
    }

    @Override
    public int getHumanEmployeeCount(String employerId) {
        return (int) contractDao.getEmploymentContractsForEmployer(employerId).stream()
                .filter(Contract::isActive).count();
    }

    @Override
    public List<Contract> getAgentContracts(String agentId) {
        return contractDao.getContractsForAgent(agentId);
    }

    private void handleContractEnd(Contract contract) {
        if (contract.type() == Contract.ContractType.EMPLOYMENT) {
            Agent employee = agentDao.getAgent(contract.partyBId());
            if (employee != null) {
                // Check if they have other employment
                boolean stillEmployed = contractDao.getEmploymentContractsForEmployee(contract.partyBId())
                        .stream().anyMatch(c -> c.isActive() && !c.id().equals(contract.id()));
                if (!stillEmployed) {
                    employee.state().setEmploymentStatus(EmploymentStatus.UNEMPLOYED);
                }
            }
            // Update employer's human employee count
            Agent employer = agentDao.getAgent(contract.partyAId());
            if (employer != null) {
                employer.state().setHumanEmployees(getHumanEmployeeCount(contract.partyAId()));
            }
        }
    }
}
