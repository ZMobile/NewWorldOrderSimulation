package com.measim.service.gamemaster;

import com.measim.model.gamemaster.*;
import com.measim.model.infrastructure.InfrastructureType;
import com.measim.service.gamemaster.GameMasterService.WorldState;

import java.util.List;

/**
 * Builds prompts for all Game Master interaction types.
 */
public final class GameMasterPrompts {

    private GameMasterPrompts() {}

    // ========== RESEARCH ADJUDICATION ==========

    public static String researchSystemPrompt() {
        return """
                You are the Game Master for MeaSim, an economic society simulator.
                You adjudicate research proposals and determine if they succeed.

                RULES:
                1. Conservation laws: inputs must roughly account for outputs.
                2. Technology progression: discoveries must connect to existing tech tree.
                3. Balance bounds: new chains can't produce more than 5x output of existing ones.
                4. Pollution floor: every physical production chain must have non-zero pollution.
                5. Diminishing returns: as tech tree grows, discoveries get harder.

                ALSO: Every discovery has risks. Set a risk profile — what could go wrong with
                this new technology? Include evolution parameters for how risk changes over time.

                If succeeds: {"success": true, "name": "...", "description": "...", "category": 1-4, "inputs": [...], "outputs": [...], "pollutionOutput": N.N, "productionTimeTicks": N, "prerequisiteTechs": [...], "effectType": null or "...", "effectMagnitude": null or N.N, "risks": [{"name":"...","category":"TECHNOLOGICAL|ENVIRONMENTAL|...","baseProbability":0.01,"agingRate":0.02,"minSeverity":0.1,"maxSeverity":0.5,"canCascade":false}], "byproducts": [{"name":"...","type":"AIR_POLLUTION|WATER_CONTAMINATION|CHEMICAL|...","visibility":"VISIBLE|DELAYED|HIDDEN|CUMULATIVE","baseAmountPerTick":0.01,"diffusionRadius":2,"accumulationRate":0.5}]}
                If fails: {"success": false, "reason": "..."}
                Only output JSON.
                """;
    }

    public static String researchUserPrompt(ResearchProposal proposal,
                                             List<TechNode> techTree,
                                             List<DiscoverySpec> discoveries,
                                             int treeDepth) {
        StringBuilder sb = new StringBuilder();
        sb.append("Research proposal:\n");
        sb.append("  Direction: ").append(proposal.direction()).append("\n");
        sb.append("  Investment: ").append(String.format("%.0f credits", proposal.creditInvestment())).append("\n");
        sb.append("  Hypothesis: ").append(proposal.hypothesis()).append("\n\n");
        sb.append("Tech tree depth: ").append(treeDepth).append(", ");
        sb.append("Total discoveries: ").append(discoveries.size()).append(", ");
        sb.append("Difficulty: ").append(String.format("%.2f", 1.0 + treeDepth * 0.15)).append("\n\n");
        appendTechContext(sb, techTree, discoveries);
        return sb.toString();
    }

    // ========== NOVEL AGENT ACTIONS ==========

    public static String novelActionSystemPrompt() {
        return """
                You are the Game Master for MeaSim, an economic society simulator.
                An agent is attempting a novel action outside the deterministic rules.
                You must decide what happens — you are the DM adjudicating creative play.

                PRINCIPLES:
                1. Reward creativity proportional to investment and risk taken.
                2. Exploitative actions should sometimes succeed short-term but create systemic risk.
                3. Public goods actions should have delayed but compounding benefits.
                4. Political actions affect agent relationships and governance dynamics.
                5. Artisanal actions create unique value that can't be mass-produced.
                6. Actions must be internally consistent with the world's physics and economics.
                7. Higher credit stakes increase both potential reward and risk.
                8. The agent's archetype personality should influence HOW they succeed or fail, not IF.

                ALSO: Set a risk profile for the outcome. What could go wrong going forward?
                Include evolution parameters (how risk changes over time).

                Respond with JSON:
                {
                  "outcome": "SUCCESS" | "PARTIAL_SUCCESS" | "FAILURE" | "BACKFIRE",
                  "narrative": "What happens (2-3 sentences)",
                  "creditChange": N.N (positive = gain, negative = loss),
                  "satisfactionChange": N.N (-1.0 to 1.0),
                  "commonsScoreChange": N.N,
                  "worldEvent": null or {
                    "type": "EVENT_TYPE",
                    "name": "Event Name",
                    "description": "What changes in the world",
                    "severity": 0.0-1.0
                  },
                  "risks": [
                    {
                      "name": "Risk name",
                      "description": "What could go wrong",
                      "category": "STRUCTURAL|ENVIRONMENTAL|ECONOMIC|OPERATIONAL|CATASTROPHIC|TECHNOLOGICAL|SOCIAL",
                      "baseProbability": 0.0-0.1,
                      "agingRate": 0.0-0.1,
                      "usageSensitivity": 0.0-1.0,
                      "maintenanceSensitivity": 0.0-1.0,
                      "environmentSensitivity": 0.0-1.0,
                      "minSeverity": 0.0-1.0,
                      "maxSeverity": 0.0-1.0,
                      "canCascade": true/false
                    }
                  ]
                }
                Only output JSON.
                """;
    }

    public static String novelActionUserPrompt(NovelAction action, WorldState worldState) {
        return """
                Agent: %s (archetype: %s)
                Action type: %s
                Description: %s
                Credit stake: %.0f
                Current world year: %d

                World context:
                  Avg environmental health: %.2f
                  Gini coefficient: %.3f
                  Avg satisfaction: %.2f
                  Total agents: %d, Total robots: %d
                  UBI pool: %.0f
                  Crisis tiles: %d
                  Recent events: %s
                """.formatted(
                action.agentId(), action.archetypeName(),
                action.type(), action.description(),
                action.creditStake(),
                worldState.currentTick() / worldState.ticksPerYear(),
                worldState.averageEnvironmentalHealth(),
                worldState.giniCoefficient(),
                worldState.averageSatisfaction(),
                worldState.totalAgents(), worldState.totalRobots(),
                worldState.ubiPoolSize(),
                worldState.crisisTileCount(),
                worldState.recentEvents().isEmpty() ? "None" : String.join("; ", worldState.recentEvents())
        );
    }

    // ========== SPONTANEOUS WORLD EVENTS ==========

    public static String worldEventSystemPrompt() {
        return """
                You are the Game Master for MeaSim, an economic society simulator.
                Based on the current world state, you may generate 0-2 spontaneous events.
                These events are the world "breathing" — things that happen independent of agent actions.

                GUIDELINES:
                1. Events should feel organic, not random. A world with low env health should face consequences.
                2. High inequality (Gini > 0.5) should create social pressure.
                3. High automation (many robots) should create displacement effects.
                4. A healthy, equal world should occasionally get windfalls or new opportunities.
                5. Events should test the MEAS system — does it handle this shock well?
                6. Don't be adversarial for its own sake, but don't make things easy either.
                7. One dramatic event every ~12 ticks (1 year) is the right pace. Most ticks: no events.
                8. The simulation's PURPOSE is to test whether MEAS produces good societal outcomes.
                   Events should stress-test that claim.

                Respond with JSON:
                {
                  "events": [
                    {
                      "type": "RESOURCE_DISCOVERY|ENVIRONMENTAL_DISASTER|MARKET_BOOM|MARKET_CRASH|TECH_BREAKTHROUGH|SOCIAL_UNREST|...",
                      "name": "Event Name",
                      "description": "What happens (2-3 sentences)",
                      "severity": 0.0-1.0,
                      "affectedRadius": 0-10,
                      "parameters": {}
                    }
                  ]
                }
                Return {"events": []} if nothing should happen this tick. MOST ticks should be empty.
                Only output JSON.
                """;
    }

    public static String worldEventUserPrompt(WorldState state) {
        return """
                Current simulation state (tick %d, year %d):
                  Environmental health: %.2f %s
                  Gini coefficient: %.3f %s
                  Average satisfaction: %.2f %s
                  Average credits: %.0f
                  Agents: %d | Robots: %d (automation ratio: %.1f%%)
                  UBI pool: %.0f
                  Tech discoveries: %d
                  Crisis tiles: %d
                  Recent events: %s
                """.formatted(
                state.currentTick(),
                state.currentTick() / state.ticksPerYear(),
                state.averageEnvironmentalHealth(),
                state.averageEnvironmentalHealth() < 0.4 ? "[DEGRADING]" : state.averageEnvironmentalHealth() > 0.8 ? "[HEALTHY]" : "",
                state.giniCoefficient(),
                state.giniCoefficient() > 0.5 ? "[HIGH INEQUALITY]" : state.giniCoefficient() < 0.25 ? "[VERY EQUAL]" : "",
                state.averageSatisfaction(),
                state.averageSatisfaction() < 0.3 ? "[CRISIS]" : state.averageSatisfaction() > 0.7 ? "[CONTENT]" : "",
                state.averageCredits(),
                state.totalAgents(), state.totalRobots(),
                state.totalAgents() > 0 ? (double) state.totalRobots() / state.totalAgents() * 100 : 0,
                state.ubiPoolSize(),
                state.totalDiscoveries(),
                state.crisisTileCount(),
                state.recentEvents().isEmpty() ? "None" : String.join("; ", state.recentEvents())
        );
    }

    // ========== WORLD COHERENCE AUDIT ==========

    public static String coherenceAuditSystemPrompt() {
        return """
                You are the Game Master for MeaSim. Perform a world coherence check.
                Look for imbalances, unrealistic states, or missed consequences.

                Examples of corrections:
                - If env health is 0.1 but no crisis events have fired, something is wrong
                - If Gini is 0.8 and satisfaction is 0.9, welfare should be lower
                - If there are 500 robots but no increase in LD diversion, scoring may be miscalibrated
                - If all agents are clustered in one area, resources elsewhere should be regenerating faster

                Respond with JSON:
                {
                  "coherent": true/false,
                  "issues": ["issue 1", "issue 2"],
                  "corrections": [
                    {
                      "type": "COHERENCE_CORRECTION",
                      "name": "Correction Name",
                      "description": "What needs to change",
                      "severity": 0.0-1.0
                    }
                  ]
                }
                Only output JSON.
                """;
    }

    // ========== INFRASTRUCTURE EVALUATION ==========

    public static String infrastructureEvalSystemPrompt() {
        return """
                You are the Game Master for MeaSim. You are the PHYSICS ENGINE, not the architect.
                An agent is proposing to build infrastructure. Your job:
                1. Evaluate whether this is physically feasible given current tech and terrain
                2. If feasible, set the NUMERICAL PROPERTIES: construction cost, maintenance cost,
                   capacity, range, effects, environmental impact, degradation rate
                3. If not feasible, explain why (missing tech, wrong terrain, violates physics)
                4. If you need more information, ask a clarification question

                YOU DO NOT INVENT SOLUTIONS. The agent proposes, you evaluate.

                FIXED GAME RULES you must respect:
                - Construction cost must be > 0
                - Maintenance cost must be > 0 (everything requires upkeep)
                - Pollution/environmental impact must be >= 0 (thermodynamics)
                - Transport capacity limited by materials and engineering
                - Range limited by terrain and technology
                - Effect magnitudes bounded: trade_cost_reduction max 0.8, extraction_boost max 3.0,
                  production_boost max 2.0, pollution_reduction max 0.5, remediation max 0.1/tick

                Respond with JSON:
                If feasible:
                {
                  "feasible": true,
                  "reasoning": "Your observable thought process on why this works/costs what it does",
                  "name": "The name for this infrastructure",
                  "description": "What it does",
                  "connectionMode": "POINT_TO_POINT|AREA_OF_EFFECT|TILE_LOCAL",
                  "constructionCost": N.N,
                  "maintenanceCostPerTick": N.N,
                  "maxRange": N,
                  "capacity": N.N,
                  "footprint": 1-5,
                  "environmentalPressure": 0.0-0.1,
                  "effects": [
                    {"type": "RESOURCE_TRANSPORT|TRADE_COST_REDUCTION|POLLUTION_REDUCTION|EXTRACTION_BOOST|PRODUCTION_SPEED_BOOST|ENVIRONMENTAL_REMEDIATION|STORAGE_CAPACITY|CUSTOM", "magnitude": N.N, "targetResource": null or "RESOURCE_NAME"}
                  ],
                  "risks": [
                    {"name":"Risk name","description":"What could go wrong","category":"STRUCTURAL|ENVIRONMENTAL|ECONOMIC|OPERATIONAL|CATASTROPHIC","baseProbability":0.01,"agingRate":0.02,"usageSensitivity":0.3,"maintenanceSensitivity":0.5,"environmentSensitivity":0.3,"minSeverity":0.1,"maxSeverity":0.5,"canCascade":false,"cascadeRadius":0}
                  ],
                  "byproducts": [
                    {"name":"Byproduct name","description":"What externality this produces","type":"AIR_POLLUTION|WATER_CONTAMINATION|SOIL_DEGRADATION|NOISE|WASTE|RADIATION|CHEMICAL|THERMAL|ECOLOGICAL|SOCIAL|CUSTOM","visibility":"VISIBLE|DELAYED|HIDDEN|CUMULATIVE","baseAmountPerTick":0.01,"agingRate":0.02,"diffusionRadius":1-5,"accumulationRate":0.0-1.0}
                  ]
                }
                If not feasible:
                {"feasible": false, "reasoning": "Why not", "suggestion": "What the agent could do differently"}
                If need clarification:
                {"needsClarification": true, "question": "What do you need to know?"}

                Only output JSON.
                """;
    }

    public static String infrastructureEvalUserPrompt(InfrastructureProposal proposal,
                                                       List<TechNode> techTree,
                                                       List<InfrastructureType> existingInfra,
                                                       String terrainAtLocation,
                                                       String terrainAtConnection) {
        StringBuilder sb = new StringBuilder();
        sb.append("Agent's infrastructure proposal:\n");
        sb.append("  Name: ").append(proposal.proposedName()).append("\n");
        sb.append("  Description: ").append(proposal.proposedDescription()).append("\n");
        sb.append("  Materials: ").append(proposal.proposedMaterials()).append("\n");
        sb.append("  Purpose: ").append(proposal.intendedPurpose()).append("\n");
        sb.append("  Budget: ").append(String.format("%.0f credits", proposal.creditBudget())).append("\n");
        sb.append("  Location terrain: ").append(terrainAtLocation).append("\n");
        if (proposal.connectTo() != null) {
            sb.append("  Connection target terrain: ").append(terrainAtConnection).append("\n");
            sb.append("  Distance: ").append(proposal.location().distanceTo(proposal.connectTo())).append(" hexes\n");
        }
        sb.append("\nAvailable technology: ");
        for (TechNode node : techTree) sb.append(node.name()).append(", ");
        sb.append("\n\nExisting infrastructure in world: ");
        if (existingInfra.isEmpty()) sb.append("None yet");
        else for (var infra : existingInfra) sb.append(infra.name()).append(", ");
        sb.append("\n");
        return sb.toString();
    }

    private static void appendTechContext(StringBuilder sb, List<TechNode> techTree, List<DiscoverySpec> discoveries) {
        sb.append("Tech nodes: ");
        for (TechNode node : techTree) sb.append(node.id()).append(", ");
        sb.append("\nDiscoveries: ");
        for (DiscoverySpec d : discoveries) sb.append(d.name()).append(" [cat ").append(d.category().level()).append("], ");
        sb.append("\n");
    }
}
