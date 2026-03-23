package com.measim.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.measim.model.agent.AgentAction;
import com.measim.model.economy.ItemType;
import com.measim.model.world.HexCoord;

/**
 * Parses LLM JSON responses into concrete AgentAction instances.
 */
public final class LlmResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmResponseParser() {}

    public static AgentAction parseAgentAction(String responseContent) {
        try {
            // Extract JSON from response (handle markdown code blocks)
            String json = responseContent.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }

            JsonNode root = MAPPER.readTree(json);
            String action = root.path("action").asText("IDLE");

            return switch (action.toUpperCase()) {
                case "PRODUCE" -> new AgentAction.Produce(root.path("chainId").asText());
                case "BUY" -> new AgentAction.PlaceBuyOrder(
                        ItemType.custom(root.path("item").asText()),
                        root.path("quantity").asInt(1),
                        root.path("maxPrice").asDouble(10.0));
                case "SELL" -> new AgentAction.PlaceSellOrder(
                        ItemType.custom(root.path("item").asText()),
                        root.path("quantity").asInt(1),
                        root.path("minPrice").asDouble(1.0));
                case "MOVE" -> new AgentAction.Move(
                        new HexCoord(root.path("q").asInt(), root.path("r").asInt()));
                case "INVEST_RESEARCH" -> new AgentAction.InvestResearch(
                        root.path("direction").asText("general"),
                        root.path("credits").asDouble(100));
                case "CONTRIBUTE_COMMONS" -> new AgentAction.ContributeCommons(
                        root.path("description").asText("public goods"),
                        root.path("credits").asDouble(50));
                case "PURCHASE_ROBOT" -> new AgentAction.PurchaseRobot();
                case "PROPOSE_GOVERNANCE" -> new AgentAction.ProposeGovernance(
                        root.path("proposal").asText());
                default -> new AgentAction.Idle();
            };
        } catch (Exception e) {
            return new AgentAction.Idle();
        }
    }
}
