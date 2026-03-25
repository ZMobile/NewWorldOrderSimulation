package com.measim.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.measim.model.agent.AgentAction;
import com.measim.model.economy.ItemType;
import com.measim.model.world.HexCoord;

import java.util.Map;

/**
 * Parses LLM JSON responses into concrete AgentAction instances.
 */
public final class LlmResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmResponseParser() {}

    /**
     * Parse multiple actions from a single LLM response.
     * Accepts both single object {"action":"..."} and array [{"action":"..."},...]
     */
    public static java.util.List<AgentAction> parseAgentActions(String responseContent) {
        try {
            String json = responseContent.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }
            JsonNode root = MAPPER.readTree(json);

            if (root.isArray()) {
                java.util.List<AgentAction> actions = new java.util.ArrayList<>();
                for (JsonNode node : root) {
                    AgentAction a = parseSingleAction(node);
                    if (!(a instanceof AgentAction.Idle)) actions.add(a);
                }
                return actions.isEmpty() ? java.util.List.of(new AgentAction.Idle()) : actions;
            } else {
                return java.util.List.of(parseSingleAction(root));
            }
        } catch (Exception e) {
            return java.util.List.of(new AgentAction.Idle());
        }
    }

    /** Single action parse — kept for backwards compatibility. */
    public static AgentAction parseAgentAction(String responseContent) {
        return parseAgentActions(responseContent).getFirst();
    }

    private static AgentAction parseSingleAction(JsonNode root) {
        try {
            String action = root.path("action").asText("IDLE");

            return switch (action.toUpperCase()) {
                case "MOVE" -> new AgentAction.Move(
                        new HexCoord(root.path("q").asInt(), root.path("r").asInt()));
                case "INVEST_RESEARCH" -> new AgentAction.InvestResearch(
                        root.path("direction").asText("general"),
                        root.path("credits").asDouble(100));
                case "CONTRIBUTE_COMMONS" -> new AgentAction.ContributeCommons(
                        root.path("description").asText("public goods"),
                        root.path("credits").asDouble(50));
                case "PURCHASE_ROBOT" -> new AgentAction.PurchaseRobot();
                case "BUILD_INFRASTRUCTURE" -> new AgentAction.BuildInfrastructure(
                        root.path("name").asText("Proposed infrastructure"),
                        root.has("connectTo") && !root.path("connectTo").isNull()
                                ? new HexCoord(root.path("connectTo").path("q").asInt(), root.path("connectTo").path("r").asInt())
                                : null,
                        null);
                case "CREATE_SERVICE" -> new AgentAction.CreateService(
                        root.path("name").asText("Proposed service"),
                        root.path("description").asText(""),
                        root.path("category").asText("CUSTOM"),
                        null, root.path("budget").asDouble(100));
                case "CONSUME_SERVICE" -> new AgentAction.ConsumeService(
                        root.path("serviceId").asText());
                case "FREE_FORM" -> new AgentAction.FreeFormAction(
                        root.path("description").asText(""),
                        root.path("budget").asDouble(100));
                case "OFFER_TRADE" -> {
                    Map<ItemType, Integer> offered = new java.util.HashMap<>();
                    Map<ItemType, Integer> requested = new java.util.HashMap<>();
                    var offeredNode = root.path("itemsOffered");
                    if (offeredNode.isObject()) offeredNode.fields().forEachRemaining(
                            e -> offered.put(ItemType.custom(e.getKey()), e.getValue().asInt()));
                    var requestedNode = root.path("itemsRequested");
                    if (requestedNode.isObject()) requestedNode.fields().forEachRemaining(
                            e -> requested.put(ItemType.custom(e.getKey()), e.getValue().asInt()));
                    yield new AgentAction.OfferTrade(
                            root.has("targetAgent") ? root.path("targetAgent").asText() : null,
                            offered, requested,
                            root.path("creditsOffered").asDouble(0),
                            root.path("creditsRequested").asDouble(0),
                            root.path("message").asText(""));
                }
                case "ACCEPT_TRADE" -> new AgentAction.AcceptTrade(root.path("offerId").asText());
                case "REJECT_TRADE" -> new AgentAction.RejectTrade(root.path("offerId").asText());
                case "SEND_MESSAGE" -> new AgentAction.SendMessage(
                        root.path("targetAgent").asText(),
                        root.path("message").asText(""));
                case "BROADCAST" -> new AgentAction.BroadcastMessage(
                        root.path("message").asText(""));
                case "OFFER_JOB" -> new AgentAction.OfferJob(
                        root.path("targetAgent").asText(),
                        root.path("wagesPerTick").asDouble(5),
                        root.path("durationTicks").asInt(12),
                        root.path("description").asText("Work agreement"));
                case "ACCEPT_JOB" -> new AgentAction.AcceptJob(
                        root.path("offererAgent").asText());
                case "PROPOSE_CONTRACT" -> new AgentAction.ProposeContract(
                        root.path("targetAgent").asText(),
                        root.path("contractType").asText("WORK_RELATION"),
                        root.path("valuePerTick").asDouble(5),
                        root.path("durationTicks").asInt(12),
                        root.path("terms").asText(""));
                case "ACCEPT_CONTRACT" -> new AgentAction.AcceptContract(
                        root.path("proposerAgent").asText(),
                        root.path("contractType").asText("WORK_RELATION"));
                case "TERMINATE_CONTRACT" -> new AgentAction.TerminateContract(
                        root.path("contractId").asText(),
                        root.path("reason").asText(""));
                case "CLAIM_PROPERTY" -> new AgentAction.ClaimProperty(
                        new HexCoord(root.path("q").asInt(), root.path("r").asInt()));
                case "ACCEPT_PROPOSAL" -> new AgentAction.AcceptProposal(
                        root.path("proposalId").asText());
                case "REJECT_PROPOSAL" -> new AgentAction.RejectProposal(
                        root.path("proposalId").asText());
                default -> new AgentAction.Idle();
            };
        } catch (Exception e) {
            return new AgentAction.Idle();
        }
    }
}

