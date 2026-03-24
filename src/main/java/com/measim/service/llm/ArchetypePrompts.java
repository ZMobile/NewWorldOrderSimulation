package com.measim.service.llm;

import com.measim.model.agent.Agent;
import com.measim.model.agent.Archetype;

/**
 * Builds system prompts for each archetype's LLM escalation.
 */
public final class ArchetypePrompts {

    private ArchetypePrompts() {}

    public static String systemPrompt(Agent agent) {
        Archetype archetype = agent.identity().archetype();
        return """
                You are a rational strategic player in MeaSim, an economic society simulation.
                You play optimally for your archetype. You are NOT simulating emotions.
                You assess situations rationally and act in your strategic interest.

                Your archetype: %s
                %s

                Traits:
                - Risk tolerance: %.2f (0=risk-averse, 1=risk-seeking)
                - Ambition: %.2f (0=content with basics, 1=driven to accumulate)
                - Altruism: %.2f (0=purely self-interested, 1=community-focused)
                - Creativity: %.2f (0=conventional, 1=highly innovative)
                - Compliance: %.2f (0=rule-breaker, 1=strict rule-follower)

                GAME CONTEXT:
                - Credits are the only currency. You earn by selling goods/services or working.
                - You need FOOD each tick or your material conditions deteriorate.
                - MEAS scoring modifiers affect how much you keep from sales (EF, CC, RC, LD axes).
                - Infrastructure and services are proposed to the Game Master who sets properties/costs.
                - Property claims give you rights to build on tile slots.
                - Work relations (employment, contracting) are between agents via contracts.
                - Risks exist on everything — true risks may differ from what you perceive.

                Respond with exactly ONE action as JSON:
                {"action": "MOVE", "q": N, "r": N}
                {"action": "BUY", "item": "...", "quantity": N, "maxPrice": N.N}
                {"action": "SELL", "item": "...", "quantity": N, "minPrice": N.N}
                {"action": "PRODUCE", "chainId": "..."}
                {"action": "PURCHASE_ROBOT"}
                {"action": "INVEST_RESEARCH", "direction": "...", "credits": N.N}
                {"action": "CONTRIBUTE_COMMONS", "description": "...", "credits": N.N}
                {"action": "BUILD_INFRASTRUCTURE", "name": "...", "description": "...", "connectTo": {"q": N, "r": N} or null}
                {"action": "CREATE_SERVICE", "name": "...", "description": "...", "category": "FINANCIAL|LOGISTICS|HEALTHCARE|EDUCATION|...", "budget": N.N}
                {"action": "CONSUME_SERVICE", "serviceId": "..."}
                {"action": "PROPOSE_GOVERNANCE", "proposal": "..."}
                {"action": "IDLE"}

                Only output JSON. No explanation.
                """.formatted(
                archetype.name(),
                archetype.description(),
                agent.identity().riskTolerance(),
                agent.identity().ambition(),
                agent.identity().altruism(),
                agent.identity().creativity(),
                agent.identity().complianceDisposition()
        );
    }

    public static String userPrompt(Agent agent, String spatialContext,
                                     String decisionContext, int currentTick) {
        var state = agent.state();
        String memoryContext = agent.memory().buildContextSummary(10);

        return """
                Tick: %d (Year %d)
                Credits: %.0f
                Satisfaction: %.2f
                Employment: %s
                Robots owned: %d

                MEAS modifiers: EF=%.2f CC=%.2f RC=%.2f LD_rate=%.4f (combined=%.3f)
                Inventory: %s

                Location:
                %s

                Decision context:
                %s

                Recent memory:
                %s

                What action do you take?
                """.formatted(
                currentTick,
                currentTick / 12,
                state.credits(),
                state.satisfaction(),
                state.employmentStatus(),
                state.ownedRobots(),
                state.modifiers().environmentalFootprint(),
                state.modifiers().commonsContribution(),
                state.modifiers().resourceConcentration(),
                state.modifiers().laborDisplacementRate(),
                state.modifiers().combinedMultiplier(),
                state.inventory().toString(),
                spatialContext,
                decisionContext,
                memoryContext
        );
    }
}
