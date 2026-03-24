package com.measim.service.simulation.phases;

import com.measim.service.agentservice.AgentServiceManager;
import com.measim.service.contract.ContractService;
import com.measim.service.property.PropertyService;
import com.measim.service.simulation.TickPhase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Phase 4.5: Process contracts (wages, rent, service subscriptions), property rents, service ticks.
 * Between market resolution (4) and scoring (5) so payments are settled before scoring.
 */
@Singleton
public class ContractPhase implements TickPhase {
    private final ContractService contractService;
    private final PropertyService propertyService;
    private final AgentServiceManager serviceManager;

    @Inject
    public ContractPhase(ContractService contractService, PropertyService propertyService,
                          AgentServiceManager serviceManager) {
        this.contractService = contractService;
        this.propertyService = propertyService;
        this.serviceManager = serviceManager;
    }

    @Override public String name() { return "Contracts"; }
    @Override public int order() { return 50; }

    @Override
    public void execute(int currentTick) {
        // Process employment contracts: pay wages, detect breaches
        contractService.processContracts(currentTick);

        // Process property rent payments
        propertyService.processRentPayments(currentTick);

        // Process services: operating costs, reputation, capacity reset
        serviceManager.tickServices(currentTick);
    }
}
