package com.measim.service.simulation.phases;

import com.measim.service.governance.GovernanceService;
import com.measim.service.simulation.TickPhase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class GovernancePhase implements TickPhase {
    private final GovernanceService governanceService;

    @Inject
    public GovernancePhase(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @Override public String name() { return "Governance"; }
    @Override public int order() { return 7; }

    @Override
    public void execute(int currentTick) {
        governanceService.processProposals(currentTick);
        governanceService.processDisputes(currentTick);
    }
}
