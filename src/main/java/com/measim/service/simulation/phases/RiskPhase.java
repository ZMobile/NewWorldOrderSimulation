package com.measim.service.simulation.phases;

import com.measim.service.risk.RiskService;
import com.measim.service.simulation.TickPhase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Phase 8.5 (between Environment and Events): Evaluate and apply risks.
 * Deterministic probability checks. GM adjudicates consequences only when triggered.
 */
@Singleton
public class RiskPhase implements TickPhase {

    private final RiskService riskService;

    @Inject
    public RiskPhase(RiskService riskService) { this.riskService = riskService; }

    @Override public String name() { return "Risk"; }
    @Override public int order() { return 100; }

    @Override
    public void execute(int currentTick) {
        var triggered = riskService.evaluateRisks(currentTick);
        if (!triggered.isEmpty()) {
            riskService.applyConsequences(triggered, currentTick);
            riskService.propagateCascades(triggered, currentTick);
            for (var event : triggered) {
                System.out.printf("    [Risk] %s on %s: %s (severity %.2f)%n",
                        event.riskName(), event.entityId(), event.gmNarrative(), event.severity());
            }
        }
    }
}
