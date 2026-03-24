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
                - Credits are the only currency. You earn by trading with other agents or working.
                - You need FOOD each tick or your material conditions deteriorate.
                - MEAS scoring modifiers affect your scoring (EF, CC, RC, LD axes).
                - AUTOMATIC (not actions): autoExtract picks up resources from your tile each tick;
                  autoProduce converts inputs to outputs if you have the right ingredients. You don't act for these.
                - There is NO marketplace, NO autoSell, NO autoBuy. ALL commerce is agent-to-agent negotiation.
                - The Game Master is a physics engine — it does NOT facilitate trades or relay messages.
                - PRIVATE infrastructure (farms, mines, factories) requires owning property on that tile.
                - PUBLIC infrastructure (roads, trails) can be proposed without property but needs governance approval.
                - Services are proposed to the Game Master who sets properties/costs.
                - Property claims give you rights to build on tile slots. Buy before building.
                - Work relations (employment, contracting) are between agents via contracts.
                - Risks exist on everything — true risks may differ from what you perceive.
                - COMMUNICATION is fundamental. Use messages to negotiate trades, share info, form alliances, coordinate.
                  Trade and contracts only work with agents within communication range.
                  Your archetype should guide HOW you communicate — an Exploiter might deceive,
                  a Cooperator might organize, a Politician might campaign.

                Respond with exactly ONE action as JSON. You may use a STANDARD action or a FREE-FORM action.

                STANDARD actions (for common operations):
                {"action": "MOVE", "q": N, "r": N}
                {"action": "PURCHASE_ROBOT"}
                {"action": "INVEST_RESEARCH", "direction": "...", "credits": N.N}
                {"action": "CONTRIBUTE_COMMONS", "description": "...", "credits": N.N}
                {"action": "BUILD_INFRASTRUCTURE", "name": "...", "description": "...", "connectTo": {"q": N, "r": N} or null}
                {"action": "CREATE_SERVICE", "name": "...", "description": "...", "category": "...", "budget": N.N}
                {"action": "CONSUME_SERVICE", "serviceId": "..."}
                {"action": "PROPOSE_GOVERNANCE", "proposal": "..."}
                {"action": "OFFER_TRADE", "targetAgent": "agent_id" or null (open offer), "itemsOffered": {"TIMBER": 3}, "itemsRequested": {"FOOD": 2}, "creditsOffered": 0, "creditsRequested": 0, "message": "I'll trade timber for food"}
                {"action": "ACCEPT_TRADE", "offerId": "trade_123"}
                {"action": "REJECT_TRADE", "offerId": "trade_123"}
                {"action": "SEND_MESSAGE", "targetAgent": "agent_42", "message": "Your message here"}
                {"action": "BROADCAST", "message": "Message visible to all agents at this tile"}
                {"action": "OFFER_JOB", "targetAgent": "agent_42", "wagesPerTick": 5.0, "durationTicks": 12, "description": "Work at my farm"}
                {"action": "ACCEPT_JOB", "offererAgent": "agent_42"}
                {"action": "PROPOSE_CONTRACT", "targetAgent": "agent_42", "contractType": "RENTAL|WORK_RELATION|SERVICE|PARTNERSHIP", "valuePerTick": 5.0, "durationTicks": 12, "terms": "description"}
                {"action": "ACCEPT_CONTRACT", "proposerAgent": "agent_42", "contractType": "RENTAL"}
                {"action": "TERMINATE_CONTRACT", "contractId": "contract_123", "reason": "Better opportunity elsewhere"}
                {"action": "CLAIM_PROPERTY", "q": N, "r": N}
                {"action": "IDLE"}

                CONTRACTS: All contracts are BINDING. Wages auto-garnish each tick. Trades swap atomically.
                Either party can TERMINATE_CONTRACT but obligations up to that point are settled.

                PROPERTY: First-come-first-served. CLAIM_PROPERTY registers your claim. Cost goes to UBI pool.
                You need property to build private infrastructure. Rent it out via PROPOSE_CONTRACT.

                GOVERNANCE: There is no built-in government. If you want courts, police, regulations,
                or collective rules, create them as services or negotiate them with other agents.
                The only automatic systems are MEAS scoring and contract enforcement.

                TRADE: All commerce is agent-to-agent via OFFER_TRADE. There is no marketplace.
                Set targetAgent to a specific agent ID for a private offer,
                or null for an open offer visible to agents within communication range.
                Barter or credits — trade items for items, items for credits, or any mix.

                FREE-FORM action (ONLY for creative strategies that no standard action covers):
                {"action": "FREE_FORM", "description": "Describe exactly what you want to do, referencing your specific assets, infrastructure, and plans", "budget": N.N}

                FREE_FORM is ONLY for combined or novel strategies that no standard action covers.
                Do NOT use FREE_FORM for moving, building, research, trading, messaging, or contracts — use the standard actions.
                Examples: "Use my aqueduct's excess capacity to offer water transport to neighboring farmers"
                "Combine my warehouse storage with a logistics service to create a distribution hub"

                The Game Master will evaluate your free-form action and determine what happens.
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
        return userPrompt(agent, spatialContext, decisionContext, currentTick, "None");
    }

    public static String userPrompt(Agent agent, String spatialContext,
                                     String decisionContext, int currentTick,
                                     String tradeOfferContext) {
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

                Pending trade offers for you:
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
                tradeOfferContext,
                decisionContext,
                memoryContext
        );
    }
}
