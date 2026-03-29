package com.measim.service.gamemaster;

import com.measim.dao.*;
import com.measim.model.agent.Agent;
import com.measim.model.economy.*;
import com.measim.model.infrastructure.Infrastructure;
import com.measim.model.llm.LlmRequest;
import com.measim.model.llm.LlmResponse;
import com.measim.model.service.ServiceInstance;
import com.measim.model.world.*;
import com.measim.service.property.PropertyService;
import com.measim.service.contract.ContractService;

import java.util.*;

/**
 * Tool definitions and handlers for the Game Master's world inspection capabilities.
 * The GM can call these tools during multi-turn conversations to query real simulation state.
 */
public class GmTools {

    private final WorldDao worldDao;
    private final AgentDao agentDao;
    private final MarketDao marketDao;
    private final InfrastructureDao infraDao;
    private final ServiceDao serviceDao;
    private final PropertyService propertyService;
    private final ContractService contractService;

    public GmTools(WorldDao worldDao, AgentDao agentDao, MarketDao marketDao,
                   InfrastructureDao infraDao, ServiceDao serviceDao,
                   PropertyService propertyService, ContractService contractService) {
        this.worldDao = worldDao;
        this.agentDao = agentDao;
        this.marketDao = marketDao;
        this.infraDao = infraDao;
        this.serviceDao = serviceDao;
        this.propertyService = propertyService;
        this.contractService = contractService;
    }

    /** All tool definitions the GM can use. */
    public List<LlmRequest.ToolDefinition> allTools() {
        return List.of(
                new LlmRequest.ToolDefinition("inspect_tile",
                        "Get full info about a tile: terrain, resources, structures, environment health, settlement status, agents present.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "q", Map.of("type", "integer", "description", "Axial Q coordinate"),
                                        "r", Map.of("type", "integer", "description", "Axial R coordinate")),
                                "required", List.of("q", "r"))),

                new LlmRequest.ToolDefinition("inspect_agent",
                        "Get detailed info about a specific agent: archetype, credits, inventory, location, employment, experience, robots, MERIT scores, satisfaction.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "agent_id", Map.of("type", "string", "description", "The agent's ID")),
                                "required", List.of("agent_id"))),

                new LlmRequest.ToolDefinition("list_nearby_agents",
                        "List agents within a radius of a tile, with summary info (archetype, credits, employment status).",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "q", Map.of("type", "integer", "description", "Center Q coordinate"),
                                        "r", Map.of("type", "integer", "description", "Center R coordinate"),
                                        "radius", Map.of("type", "integer", "description", "Search radius in tiles (max 10)")),
                                "required", List.of("q", "r", "radius"))),

                new LlmRequest.ToolDefinition("query_market",
                        "Get market prices, recent trade volume, and order book summary for one or all item types.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "item_type", Map.of("type", "string", "description", "Item type to query, or 'ALL' for summary of all traded items")),
                                "required", List.of("item_type"))),

                new LlmRequest.ToolDefinition("list_infrastructure",
                        "List infrastructure within a radius of a tile, with type, condition, owner, and connections.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "q", Map.of("type", "integer", "description", "Center Q coordinate"),
                                        "r", Map.of("type", "integer", "description", "Center R coordinate"),
                                        "radius", Map.of("type", "integer", "description", "Search radius in tiles (max 10)")),
                                "required", List.of("q", "r", "radius"))),

                new LlmRequest.ToolDefinition("list_services",
                        "List active services near a location, with type, quality, price, capacity, and owner.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "q", Map.of("type", "integer", "description", "Center Q coordinate"),
                                        "r", Map.of("type", "integer", "description", "Center R coordinate"),
                                        "radius", Map.of("type", "integer", "description", "Search radius (max 10)")),
                                "required", List.of("q", "r", "radius"))),

                new LlmRequest.ToolDefinition("query_contracts",
                        "Get active contracts (work relations, service agreements) for a specific agent.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "agent_id", Map.of("type", "string", "description", "The agent's ID")),
                                "required", List.of("agent_id"))),

                new LlmRequest.ToolDefinition("query_property_claims",
                        "Get property claims on a specific tile or all claims owned by an agent.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "q", Map.of("type", "integer", "description", "Tile Q coordinate (optional if agent_id provided)"),
                                        "r", Map.of("type", "integer", "description", "Tile R coordinate (optional if agent_id provided)"),
                                        "agent_id", Map.of("type", "string", "description", "Agent ID to query claims for (optional if q,r provided)")),
                                "required", List.of())),

                new LlmRequest.ToolDefinition("get_world_summary",
                        "Get high-level world statistics: population, average credits, gini coefficient, environment health, total infrastructure, total services, market activity.",
                        Map.of("type", "object",
                                "properties", Map.of(),
                                "required", List.of()))
        );
    }

    /** Execute a tool call and return the result as a string. */
    public String handleToolCall(LlmResponse.ToolUseBlock toolCall) {
        return switch (toolCall.name()) {
            case "inspect_tile" -> inspectTile(toolCall.input());
            case "inspect_agent" -> inspectAgent(toolCall.input());
            case "list_nearby_agents" -> listNearbyAgents(toolCall.input());
            case "query_market" -> queryMarket(toolCall.input());
            case "list_infrastructure" -> listInfrastructure(toolCall.input());
            case "list_services" -> listServices(toolCall.input());
            case "query_contracts" -> queryContracts(toolCall.input());
            case "query_property_claims" -> queryPropertyClaims(toolCall.input());
            case "get_world_summary" -> getWorldSummary(toolCall.input());
            default -> "Unknown tool: " + toolCall.name();
        };
    }

    // ========== Tool implementations ==========

    private String inspectTile(Map<String, Object> input) {
        int q = toInt(input.get("q"));
        int r = toInt(input.get("r"));
        HexCoord coord = new HexCoord(q, r);
        Tile tile = worldDao.getTile(coord);
        if (tile == null) return "No tile at (" + q + "," + r + ").";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Tile (%d,%d): terrain=%s, passable=%s, settlement=%s, env_health=%.2f%n",
                q, r, tile.terrain(), tile.terrain().isPassable(), tile.isSettlementZone(),
                tile.environment().averageHealth()));

        if (!tile.resources().isEmpty()) {
            sb.append("Resources: ");
            for (var res : tile.resources()) {
                sb.append(String.format("%s(abundance=%.1f, depleted=%s) ", res.type(), res.abundance(), res.isDepleted()));
            }
            sb.append("\n");
        } else {
            sb.append("Resources: none\n");
        }

        sb.append(String.format("Structures: %d, History: %d agent visits, %d productions%n",
                tile.structureIds().size(), tile.history().totalAgentVisitTicks(), tile.history().totalProductionTicks()));

        // Agents present on this tile
        List<Agent> present = agentDao.getAllAgents().stream()
                .filter(a -> a.state().location().equals(coord))
                .toList();
        if (!present.isEmpty()) {
            sb.append(String.format("Agents present (%d): ", present.size()));
            for (var a : present.stream().limit(15).toList()) {
                sb.append(String.format("%s(%s, %.0f credits) ", a.id(), a.identity().archetype(), a.state().credits()));
            }
            if (present.size() > 15) sb.append("... and ").append(present.size() - 15).append(" more");
            sb.append("\n");
        } else {
            sb.append("Agents present: none\n");
        }

        return sb.toString();
    }

    private String inspectAgent(Map<String, Object> input) {
        String agentId = (String) input.get("agent_id");
        Agent agent = agentDao.getAgent(agentId);
        if (agent == null) return "No agent found with ID: " + agentId;

        var state = agent.state();
        var profile = agent.identity();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Agent %s (%s): %s%n", agentId, profile.archetype(), agent.name()));
        sb.append(String.format("Location: (%d,%d), Credits: %.1f, Satisfaction: %.2f%n",
                state.location().q(), state.location().r(), state.credits(), state.satisfaction()));
        sb.append(String.format("Employment: %s, Robots: %d%n",
                state.employmentStatus(), state.ownedRobots()));
        sb.append(String.format("Traits: ambition=%.2f, creativity=%.2f, altruism=%.2f, riskTolerance=%.2f, compliance=%.2f%n",
                profile.ambition(), profile.creativity(), profile.altruism(),
                profile.riskTolerance(), profile.complianceDisposition()));

        // Inventory
        if (!state.inventory().isEmpty()) {
            sb.append("Inventory: ");
            for (var entry : state.inventory().entrySet()) {
                if (entry.getValue() > 0) sb.append(entry.getKey()).append("=").append(entry.getValue()).append(" ");
            }
            sb.append("\n");
        }

        // Experience
        if (!state.allExperience().isEmpty()) {
            sb.append("Experience: ");
            for (var entry : state.allExperience().entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append(" ");
            }
            sb.append("\n");
        }

        // MERIT scores
        var sv = state.scoreVector();
        sb.append(String.format("MERIT: EF=%.3f, CC=%.3f, LD=%.3f, RC=%.3f, EP=%.3f, commons=%.2f%n",
                sv.environmentalFootprint(), sv.commonsContribution(), sv.laborDisplacement(),
                sv.resourceConcentration(), sv.economicProductivity(), state.commonsScore()));

        // Recent memories (last 5)
        var memories = agent.recentMemories(5);
        if (!memories.isEmpty()) {
            sb.append("Recent memories:\n");
            for (var mem : memories) {
                sb.append(String.format("  [tick %d] %s: %s%n", mem.tick(), mem.type(), mem.description()));
            }
        }

        return sb.toString();
    }

    private String listNearbyAgents(Map<String, Object> input) {
        int q = toInt(input.get("q"));
        int r = toInt(input.get("r"));
        int radius = Math.min(toInt(input.get("radius")), 10);
        HexCoord center = new HexCoord(q, r);

        List<Agent> nearby = agentDao.getAllAgents().stream()
                .filter(a -> a.state().location().distanceTo(center) <= radius)
                .toList();

        if (nearby.isEmpty()) return String.format("No agents within %d tiles of (%d,%d).", radius, q, r);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d agents within %d tiles of (%d,%d):%n", nearby.size(), radius, q, r));
        for (var a : nearby.stream().limit(30).toList()) {
            sb.append(String.format("  %s: %s, %.0f credits, %s, at (%d,%d)%n",
                    a.id(), a.identity().archetype(), a.state().credits(),
                    a.state().employmentStatus(),
                    a.state().location().q(), a.state().location().r()));
        }
        if (nearby.size() > 30) sb.append("  ... and ").append(nearby.size() - 30).append(" more\n");
        return sb.toString();
    }

    private String queryMarket(Map<String, Object> input) {
        String itemTypeStr = (String) input.get("item_type");
        var markets = marketDao.getAllMarkets();
        if (markets.isEmpty()) return "No active markets.";

        var market = markets.iterator().next();
        StringBuilder sb = new StringBuilder();

        if ("ALL".equalsIgnoreCase(itemTypeStr)) {
            sb.append("Market summary (all items):\n");
            for (ResourceType r : ResourceType.values()) {
                ItemType item = ItemType.of(r);
                double price = market.getLastPrice(item);
                if (price > 0) sb.append(String.format("  %s: last price=%.2f%n", item, price));
            }
            for (ProductType p : ProductType.values()) {
                ItemType item = ItemType.of(p);
                double price = market.getLastPrice(item);
                if (price > 0) sb.append(String.format("  %s: last price=%.2f%n", item, price));
            }
        } else {
            // Try to find the specific item
            sb.append(String.format("Market data for '%s':%n", itemTypeStr));
            // Check resources
            for (ResourceType r : ResourceType.values()) {
                if (r.name().equalsIgnoreCase(itemTypeStr)) {
                    ItemType item = ItemType.of(r);
                    sb.append(String.format("  Last price: %.2f%n", market.getLastPrice(item)));
                    break;
                }
            }
            // Check products
            for (ProductType p : ProductType.values()) {
                if (p.name().equalsIgnoreCase(itemTypeStr)) {
                    ItemType item = ItemType.of(p);
                    sb.append(String.format("  Last price: %.2f%n", market.getLastPrice(item)));
                    break;
                }
            }
        }
        return sb.toString();
    }

    private String listInfrastructure(Map<String, Object> input) {
        int q = toInt(input.get("q"));
        int r = toInt(input.get("r"));
        int radius = Math.min(toInt(input.get("radius")), 10);
        HexCoord center = new HexCoord(q, r);

        List<Infrastructure> nearby = new ArrayList<>();
        for (Tile tile : worldDao.getTilesInRange(center, radius)) {
            nearby.addAll(infraDao.getAtTile(tile.coord()));
        }

        if (nearby.isEmpty()) return String.format("No infrastructure within %d tiles of (%d,%d).", radius, q, r);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d infrastructure within %d tiles of (%d,%d):%n", nearby.size(), radius, q, r));
        for (var infra : nearby.stream().limit(20).toList()) {
            sb.append(String.format("  %s: %s, condition=%.0f%%, owner=%s, at (%d,%d)",
                    infra.id(), infra.type().name(), infra.condition() * 100,
                    infra.ownerId(), infra.location().q(), infra.location().r()));
            if (infra.connectedTo() != null) {
                sb.append(String.format(" -> (%d,%d)", infra.connectedTo().q(), infra.connectedTo().r()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String listServices(Map<String, Object> input) {
        int q = toInt(input.get("q"));
        int r = toInt(input.get("r"));
        int radius = Math.min(toInt(input.get("radius")), 10);
        HexCoord center = new HexCoord(q, r);

        List<ServiceInstance> nearby = serviceDao.getInstancesNear(center, radius);

        if (nearby.isEmpty()) return String.format("No active services within %d tiles of (%d,%d).", radius, q, r);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d services within %d tiles of (%d,%d):%n", nearby.size(), radius, q, r));
        for (var svc : nearby.stream().limit(20).toList()) {
            sb.append(String.format("  %s: %s (%s), quality=%.2f, price=%.1f, owner=%s%n",
                    svc.id(), svc.type().name(), svc.type().category(),
                    svc.effectiveQuality(), svc.type().pricePerUse(), svc.ownerId()));
        }
        return sb.toString();
    }

    private String queryContracts(Map<String, Object> input) {
        String agentId = (String) input.get("agent_id");
        var contracts = contractService.getWorkRelationsOf(agentId);

        if (contracts.isEmpty()) return "No active contracts for " + agentId + ".";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d active contracts for %s:%n", contracts.size(), agentId));
        for (var c : contracts) {
            sb.append(String.format("  %s: %s, partyA=%s, partyB=%s, payment=%.1f/tick, started=%d, duration=%d%n",
                    c.id(), c.type(), c.partyAId(), c.partyBId(), c.paymentPerTick(),
                    c.startTick(), c.durationTicks()));
        }
        return sb.toString();
    }

    private String queryPropertyClaims(Map<String, Object> input) {
        String agentId = input.containsKey("agent_id") ? (String) input.get("agent_id") : null;

        if (agentId != null && !agentId.isEmpty()) {
            var claims = propertyService.getAgentProperties(agentId);
            if (claims.isEmpty()) return "No property claims for " + agentId + ".";
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%d property claims for %s:%n", claims.size(), agentId));
            for (var c : claims) {
                sb.append(String.format("  Tile (%d,%d), slots=%d, acquired tick %d%n",
                        c.tile().q(), c.tile().r(), c.slotCount(), c.acquiredTick()));
            }
            return sb.toString();
        }

        if (input.containsKey("q") && input.containsKey("r")) {
            int q = toInt(input.get("q"));
            int r = toInt(input.get("r"));
            HexCoord coord = new HexCoord(q, r);
            var claims = propertyService.getClaimsOnTile(coord);
            if (claims.isEmpty()) return String.format("No property claims on tile (%d,%d).", q, r);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Property claims on (%d,%d):%n", q, r));
            for (var c : claims) {
                sb.append(String.format("  Owner: %s, slots=%d, acquired tick %d%n",
                        c.ownerId(), c.slotCount(), c.acquiredTick()));
            }
            return sb.toString();
        }

        return "Provide either agent_id or (q, r) coordinates.";
    }

    private String getWorldSummary(Map<String, Object> input) {
        var agents = agentDao.getAllAgents();
        double avgCredits = agents.stream().mapToDouble(a -> a.state().credits()).average().orElse(0);
        double avgSatisfaction = agents.stream().mapToDouble(a -> a.state().satisfaction()).average().orElse(0);
        int totalRobots = agents.stream().mapToInt(a -> a.state().ownedRobots()).sum();
        long employed = agents.stream().filter(a ->
                a.state().employmentStatus() != com.measim.model.agent.EmploymentStatus.UNEMPLOYED).count();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("World summary:%n"));
        sb.append(String.format("  Population: %d agents%n", agents.size()));
        sb.append(String.format("  Avg credits: %.1f, Avg satisfaction: %.2f%n", avgCredits, avgSatisfaction));
        sb.append(String.format("  Employment: %d/%d (%.0f%%)%n", employed, agents.size(),
                100.0 * employed / agents.size()));
        sb.append(String.format("  Total robots: %d%n", totalRobots));
        sb.append(String.format("  Infrastructure count: %d%n", infraDao.getAll().size()));
        sb.append(String.format("  Active services: %d%n", serviceDao.getActiveInstances().size()));

        // Archetype distribution
        Map<String, Integer> archetypes = new TreeMap<>();
        for (var a : agents) archetypes.merge(a.identity().archetype().name(), 1, Integer::sum);
        sb.append("  Archetypes: ").append(archetypes).append("\n");

        return sb.toString();
    }

    // ========== Helpers ==========

    private static int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) return Integer.parseInt(s);
        return 0;
    }
}
