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

        if (type == Contract.ContractType.WORK_RELATION) {
            Agent worker = agentDao.getAgent(partyBId);
            if (worker != null) worker.state().setEmploymentStatus(EmploymentStatus.EMPLOYED);
            Agent hirer = agentDao.getAgent(partyAId);
            if (hirer != null) {
                hirer.state().setEmploymentStatus(EmploymentStatus.BUSINESS_OWNER);
                hirer.state().setHumanEmployees((int) Math.ceil(getWeightedLaborCount(partyAId)));
            }
        }

        return contract;
    }

    @Override
    public void processContracts(int currentTick) {
        for (Contract contract : contractDao.getActiveContracts()) {
            if (contract.isExpired(currentTick)) {
                contract.expire();
                handleContractEnd(contract);
                continue;
            }

            String payerId = contract.payerId();
            String payeeId = contract.payeeId();
            if (payerId == null || payeeId == null) continue;

            Agent payer = agentDao.getAgent(payerId);
            Agent payee = agentDao.getAgent(payeeId);
            if (payer == null || payee == null) continue;

            if (payer.state().spendCredits(contract.paymentPerTick())) {
                payee.state().addCredits(contract.paymentPerTick());
                payee.state().addRevenue(contract.paymentPerTick());

                if (contract.type() == Contract.ContractType.WORK_RELATION) {
                    Agent hirer = agentDao.getAgent(contract.partyAId());
                    if (hirer != null) {
                        hirer.state().setHumanEmployees((int) Math.ceil(getWeightedLaborCount(contract.partyAId())));
                    }
                }
            } else {
                contract.breach();
                handleContractEnd(contract);
                payer.addMemory(new MemoryEntry(currentTick, "CONTRACT",
                        "Breached contract: couldn't afford " + contract.paymentPerTick(),
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
    public List<Contract> getWorkRelationsOf(String hirerId) {
        return contractDao.getWorkRelationsForHirer(hirerId);
    }

    @Override
    public Optional<Contract> getWorkRelationFor(String workerId) {
        return contractDao.getWorkRelationsForWorker(workerId).stream().findFirst();
    }

    @Override
    public double getWeightedLaborCount(String hirerId) {
        return contractDao.getWorkRelationsForHirer(hirerId).stream()
                .filter(Contract::isActive)
                .mapToDouble(Contract::laborWeight)
                .sum();
    }

    @Override
    public List<Contract> getAgentContracts(String agentId) {
        return contractDao.getContractsForAgent(agentId);
    }

    private void handleContractEnd(Contract contract) {
        if (contract.type() == Contract.ContractType.WORK_RELATION) {
            Agent worker = agentDao.getAgent(contract.partyBId());
            if (worker != null) {
                boolean stillWorking = contractDao.getWorkRelationsForWorker(contract.partyBId())
                        .stream().anyMatch(c -> c.isActive() && !c.id().equals(contract.id()));
                if (!stillWorking) {
                    worker.state().setEmploymentStatus(EmploymentStatus.UNEMPLOYED);
                }
            }
            Agent hirer = agentDao.getAgent(contract.partyAId());
            if (hirer != null) {
                hirer.state().setHumanEmployees((int) Math.ceil(getWeightedLaborCount(contract.partyAId())));
            }
        }
    }
}
