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
                You are simulating an economic agent in a society simulator called MeaSim.
                You must respond with exactly ONE action in JSON format.

                Your personality: %s
                %s

                Your traits:
                - Risk tolerance: %.2f (0=risk-averse, 1=risk-seeking)
                - Ambition: %.2f (0=content with basics, 1=driven to accumulate)
                - Altruism: %.2f (0=purely self-interested, 1=community-focused)
                - Creativity: %.2f (0=conventional, 1=highly innovative)
                - Compliance: %.2f (0=rule-breaker, 1=strict rule-follower)

                Respond with a JSON object. Valid action types:
                {"action": "PRODUCE", "chainId": "..."}
                {"action": "BUY", "item": "...", "quantity": N, "maxPrice": N.N}
                {"action": "SELL", "item": "...", "quantity": N, "minPrice": N.N}
                {"action": "MOVE", "q": N, "r": N}
                {"action": "INVEST_RESEARCH", "direction": "...", "credits": N.N}
                {"action": "CONTRIBUTE_COMMONS", "description": "...", "credits": N.N}
                {"action": "PURCHASE_ROBOT"}
                {"action": "PROPOSE_GOVERNANCE", "proposal": "..."}
                {"action": "IDLE"}

                Only output the JSON. No explanation.
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
                Tick: %d
                Credits: %.0f
                Score modifiers: EF=%.2f CC=%.2f RC=%.2f LD_rate=%.4f
                Robots owned: %d
                Inventory: %s

                Location context:
                %s

                Decision context:
                %s

                Recent memory:
                %s

                What action do you take?
                """.formatted(
                currentTick,
                state.credits(),
                state.modifiers().environmentalFootprint(),
                state.modifiers().commonsContribution(),
                state.modifiers().resourceConcentration(),
                state.modifiers().laborDisplacementRate(),
                state.ownedRobots(),
                state.inventory().toString(),
                spatialContext,
                decisionContext,
                memoryContext
        );
    }
}
