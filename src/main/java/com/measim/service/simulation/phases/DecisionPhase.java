package com.measim.service.simulation.phases;

import com.measim.dao.AgentDao;
import com.measim.dao.MarketDao;
import com.measim.dao.WorldDao;
import com.measim.model.agent.Agent;
import com.measim.model.agent.AgentAction;
import com.measim.model.agent.Archetype;
import com.measim.model.config.SimulationConfig;
import com.measim.model.economy.*;
import com.measim.service.agent.AgentDecisionService;
import com.measim.service.llm.LlmService;
import com.measim.service.simulation.TickPhase;
import com.measim.service.world.PathfindingService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Phase 2: Agent decisions.
 *
 * Two tiers:
 *   Tier 1 (all agents, every tick): Deterministic utility calculator. Fast.
 *   Tier 2 (eligible agents, LLM): Creative strategic reasoning. Replaces Tier 1 action.
 *
 * LLM escalation triggers when:
 *   - Agent has enough credits to take meaningful action (>100)
 *   - Trigger conditions met based on archetype and world state
 *   - LLM budget permits
 *   - Max N agent LLM calls per tick (configurable)
 */
@Singleton
public class DecisionPhase implements TickPhase {
    private final AgentDao agentDao;
    private final MarketDao marketDao;
    private final WorldDao worldDao;
    private final AgentDecisionService decisionService;
    private final LlmService llmService;
    private final com.measim.service.trade.TradeService tradeService;
    private final com.measim.service.trade.CommunicationRangeService commRange;
    private final com.measim.dao.CommunicationDao communicationDao;
    private final SimulationConfig config;
    private final Map<String, AgentAction> pendingActions = new HashMap<>();

    @Inject
    public DecisionPhase(AgentDao agentDao, MarketDao marketDao, WorldDao worldDao,
                          AgentDecisionService decisionService, LlmService llmService,
                          com.measim.service.trade.TradeService tradeService,
                          com.measim.service.trade.CommunicationRangeService commRange,
                          com.measim.dao.CommunicationDao communicationDao,
                          SimulationConfig config) {
        this.agentDao = agentDao;
        this.marketDao = marketDao;
        this.worldDao = worldDao;
        this.decisionService = decisionService;
        this.llmService = llmService;
        this.tradeService = tradeService;
        this.commRange = commRange;
        this.communicationDao = communicationDao;
        this.config = config;
    }

    @Override public String name() { return "Decision"; }
    @Override public int order() { return 20; }

    @Override
    public void execute(int currentTick) {
        pendingActions.clear();
        Map<ItemType, Double> prices = buildPriceSnapshot();

        // Tier 1: deterministic decisions for ALL agents
        long t1Start = System.currentTimeMillis();
        for (var agent : agentDao.getAllAgents()) {
            pendingActions.put(agent.id(), decisionService.decideStrategicAction(agent, prices, currentTick));
        }
        long t1Elapsed = System.currentTimeMillis() - t1Start;

        // Summarize Tier 1 action distribution
        Map<String, Integer> actionCounts = new java.util.LinkedHashMap<>();
        for (var action : pendingActions.values()) {
            String name = action.getClass().getSimpleName();
            actionCounts.merge(name, 1, Integer::sum);
        }
        System.out.printf("    [Decision] Tier 1 (%dms): %s%n", t1Elapsed, actionCounts);
        System.out.flush();

        // Tier 2: LLM escalation for eligible agents (replaces their Tier 1 action)
        if (llmService.isAvailable()) {
            List<Agent> escalationCandidates = agentDao.getAllAgents().stream()
                    .filter(a -> shouldEscalateToLlm(a, currentTick))
                    .toList();

            if (!escalationCandidates.isEmpty()) {
                int maxCalls = config.maxAgentCallsPerTick();
                List<Agent> toEscalate = escalationCandidates.size() > maxCalls
                        ? escalationCandidates.subList(0, maxCalls)
                        : escalationCandidates;

                System.out.printf("    [Decision] Escalating %d/%d agents to LLM...%n",
                        toEscalate.size(), agentDao.getAgentCount());
                System.out.flush();

                // Fire ALL LLM calls concurrently — no batching, no caps
                long llmStart = System.currentTimeMillis();
                var futures = toEscalate.stream()
                        .map(agent -> {
                            String spatial = buildSpatialContext(agent, currentTick);
                            String decision = "Strategic decision for tick " + currentTick;
                            // Build trade context: pending offers + visible open offers
                            var incoming = tradeService.getIncomingOffers(agent.id());
                            var visible = tradeService.getVisibleOpenOffers(agent.id());
                            StringBuilder tradeSb = new StringBuilder();
                            if (!incoming.isEmpty()) {
                                tradeSb.append("Direct offers to you:\n");
                                for (var o : incoming) tradeSb.append("  ").append(o.id()).append(": ").append(o.summary()).append("\n");
                            }
                            if (!visible.isEmpty()) {
                                tradeSb.append("Open offers nearby:\n");
                                for (var o : visible) tradeSb.append("  ").append(o.id()).append(": ").append(o.summary()).append("\n");
                            }
                            String tradeCtx = tradeSb.isEmpty() ? "None" : tradeSb.toString();

                            return Map.entry(agent.id(),
                                    llmService.escalateDecision(agent, spatial, decision, currentTick, tradeCtx));
                        })
                        .toList();

                CompletableFuture.allOf(futures.stream()
                        .map(Map.Entry::getValue)
                        .toArray(CompletableFuture[]::new)).join();

                int llmActions = 0;
                for (var entry : futures) {
                    AgentAction llmAction = entry.getValue().join();
                    if (!(llmAction instanceof AgentAction.Idle)) {
                        pendingActions.put(entry.getKey(), llmAction);
                        llmActions++;
                    }
                }
                System.out.printf("    [Decision] LLM done (%dms): %d/%d produced actions%n",
                        System.currentTimeMillis() - llmStart, llmActions, toEscalate.size());
                System.out.flush();
            }
        }
    }

    public Map<String, AgentAction> pendingActions() { return Collections.unmodifiableMap(pendingActions); }

    // Interaction actions collected across micro-rounds (messages, trades, contracts)
    private final List<Map.Entry<String, AgentAction>> interactionActions =
            java.util.Collections.synchronizedList(new ArrayList<>());

    public List<Map.Entry<String, AgentAction>> interactionActions() {
        return Collections.unmodifiableList(interactionActions);
    }

    /**
     * Multi-turn agent conversations within a tick.
     * Like Catan: during your turn, you can talk freely until satisfied.
     *
     * Flow:
     *   Round 1: All agents with new messages/offers respond (concurrent)
     *   → Actions execute (messages delivered, offers posted)
     *   Round 2: Agents who received NEW responses get to reply
     *   → Actions execute
     *   Round 3: Same pattern — only agents with genuinely new input
     *   Stop: When no new messages generated, or max rounds hit
     *
     * Each agent can participate in multiple rounds IF they receive new input.
     * An agent who sent a message and got a reply can respond to the reply.
     */
    public void runInteractionRounds(int currentTick, int maxRounds) {
        if (!llmService.isAvailable()) return;
        interactionActions.clear();

        for (int round = 0; round < maxRounds; round++) {
            int msgCountBefore = communicationDao.getAllMessages().size();
            int offerCountBefore = interactionActions.size();

            // Find agents who have NEW unprocessed input this tick
            // "New" = messages/offers they haven't yet responded to
            List<Agent> responders = findAgentsWithNewInput(currentTick, round);

            if (responders.isEmpty()) {
                if (round > 0) System.out.printf("    [Interaction] Round %d: conversations settled.%n", round + 1);
                break;
            }

            System.out.printf("    [Interaction] Round %d: %d agents in conversation...%n",
                    round + 1, responders.size());
            System.out.flush();

            // All responders act concurrently
            var futures = responders.stream()
                    .map(agent -> {
                        String spatial = buildSpatialContext(agent, currentTick);
                        String context = buildConversationContext(agent, currentTick);

                        var incoming = tradeService.getIncomingOffers(agent.id());
                        var visible = tradeService.getVisibleOpenOffers(agent.id());
                        StringBuilder tradeSb = new StringBuilder();
                        if (!incoming.isEmpty()) {
                            tradeSb.append("Pending offers for you:\n");
                            for (var o : incoming) tradeSb.append("  ").append(o.id()).append(": ").append(o.summary()).append("\n");
                        }
                        if (!visible.isEmpty()) {
                            tradeSb.append("Open offers nearby:\n");
                            for (var o : visible) tradeSb.append("  ").append(o.id()).append(": ").append(o.summary()).append("\n");
                        }
                        String tradeCtx = tradeSb.isEmpty() ? "None" : tradeSb.toString();

                        return Map.entry(agent.id(),
                                llmService.escalateDecision(agent, spatial, context, currentTick, tradeCtx));
                    })
                    .toList();

            CompletableFuture.allOf(futures.stream()
                    .map(Map.Entry::getValue)
                    .toArray(CompletableFuture[]::new)).join();

            int actions = 0;
            for (var entry : futures) {
                AgentAction action = entry.getValue().join();
                if (!(action instanceof AgentAction.Idle)) {
                    interactionActions.add(Map.entry(entry.getKey(), action));
                    actions++;
                }
            }
            System.out.printf("    [Interaction] Round %d: %d actions%n", round + 1, actions);
            System.out.flush();

            if (actions == 0) break;

            // Did this round generate new messages? If not, conversations are done.
            int msgCountAfter = communicationDao.getAllMessages().size();
            if (msgCountAfter == msgCountBefore) break;
        }
    }

    // Track which messages each agent has already "seen" (responded to)
    private final Map<String, Integer> lastSeenMessageIndex = new HashMap<>();

    /**
     * Find agents who have genuinely new input to respond to.
     * "New" means messages/offers they haven't yet processed.
     */
    private List<Agent> findAgentsWithNewInput(int currentTick, int round) {
        if (round == 0) lastSeenMessageIndex.clear();

        var allMessages = communicationDao.getAllMessages();

        return agentDao.getAllAgents().stream()
                .filter(a -> {
                    String id = a.id();
                    int lastSeen = lastSeenMessageIndex.getOrDefault(id, 0);

                    // Check for pending trade offers
                    if (!tradeService.getIncomingOffers(id).isEmpty()) return true;

                    // Check for messages after their last seen index
                    for (int i = lastSeen; i < allMessages.size(); i++) {
                        var m = allMessages.get(i);
                        if (m.tick() != currentTick) continue;
                        if (m.senderId().equals(id)) continue;
                        if (m.receiverId().equals(id) || "ALL_AT_TILE".equals(m.receiverId())) {
                            if (m.channel() == com.measim.model.communication.Message.Channel.AGENT_TO_AGENT
                                    || m.channel() == com.measim.model.communication.Message.Channel.BROADCAST) {
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .peek(a -> lastSeenMessageIndex.put(a.id(), allMessages.size()))
                .toList();
    }

    /**
     * Build conversation context for an agent's interaction round.
     * Shows the recent conversation thread so the agent can respond coherently.
     */
    private String buildConversationContext(Agent agent, int currentTick) {
        StringBuilder sb = new StringBuilder();
        sb.append("You're in a conversation. Respond to messages and offers you've received.\n");
        sb.append("You can: SEND_MESSAGE, BROADCAST, OFFER_TRADE, ACCEPT_TRADE, REJECT_TRADE, ");
        sb.append("OFFER_JOB, ACCEPT_JOB, ACCEPT_CONTRACT, REJECT_TRADE, or IDLE to end your turn.\n\n");

        // Show conversation thread for this tick
        var messages = communicationDao.getAllMessages().stream()
                .filter(m -> m.tick() == currentTick)
                .filter(m -> m.senderId().equals(agent.id()) || m.receiverId().equals(agent.id())
                        || "ALL_AT_TILE".equals(m.receiverId()))
                .filter(m -> m.channel() == com.measim.model.communication.Message.Channel.AGENT_TO_AGENT
                        || m.channel() == com.measim.model.communication.Message.Channel.BROADCAST)
                .toList();

        if (!messages.isEmpty()) {
            sb.append("Conversation this tick:\n");
            for (var m : messages) {
                String dir = m.senderId().equals(agent.id()) ? "You said" : m.senderId() + " said";
                sb.append(String.format("  %s: %s\n", dir,
                        m.content().substring(0, Math.min(150, m.content().length()))));
            }
        }

        return sb.toString();
    }

    /**
     * Should this agent's decision be escalated to LLM this tick?
     * Not every agent every tick — that would be too expensive.
     * Escalation happens for specific conditions per archetype.
     */
    private boolean shouldEscalateToLlm(Agent agent, int currentTick) {
        if (agent.state().credits() < 50) return false; // too broke to do anything interesting

        var profile = agent.identity();
        return switch (profile.archetype()) {
            // Creative archetypes escalate more often
            case ENTREPRENEUR, INNOVATOR -> currentTick % 3 == 0;
            case ARTISAN, PROVIDER -> currentTick % 4 == 0;

            // Strategic archetypes escalate periodically
            case POLITICIAN, ORGANIZER, REGULATOR -> currentTick % 6 == 0;
            case SPECULATOR, LANDLORD, ACCUMULATOR -> currentTick % 4 == 0 && agent.state().credits() > 200;

            // Adversarial archetypes escalate when conditions are right
            case EXPLOITER -> currentTick % 3 == 0;
            case OPTIMIZER -> currentTick % 4 == 0 && agent.state().credits() > 300;

            // Workers/homesteaders escalate less (they're more routine)
            case WORKER, HOMESTEADER -> currentTick % 6 == 0;

            // Others
            case COOPERATOR, PHILANTHROPIST -> currentTick % 6 == 0 && agent.state().credits() > 200;
            case AUTOMATOR -> currentTick % 4 == 0;
            case FREE_RIDER -> currentTick % 12 == 0; // rarely escalates
        };
    }

    private String buildSpatialContext(Agent agent, int currentTick) {
        var loc = agent.state().location();
        var tile = worldDao.getTile(loc);
        if (tile == null) return "Unknown location.";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("At (%d,%d), terrain: %s, env health: %.2f. ",
                loc.q(), loc.r(), tile.terrain(), tile.environment().averageHealth()));
        if (!tile.resources().isEmpty()) {
            sb.append("Local resources: ");
            for (var res : tile.resources()) {
                if (!res.isDepleted()) sb.append(res.type()).append("(").append(String.format("%.0f", res.abundance())).append(") ");
            }
            sb.append("\n");
        }
        if (tile.isSettlementZone()) sb.append("Settlement zone. ");
        sb.append(String.format("Structures: %d.\n", tile.structureIds().size()));

        // Nearby agents within communication range
        int range = commRange.getEffectiveRange(agent);
        sb.append(String.format("Communication range: %d tiles.\n", range));
        var nearby = agentDao.getAllAgents().stream()
                .filter(a -> !a.id().equals(agent.id()))
                .filter(a -> a.state().location().distanceTo(loc) <= range)
                .limit(10) // cap to keep prompt size reasonable
                .toList();
        if (!nearby.isEmpty()) {
            sb.append(String.format("Nearby agents (%d within range):\n", nearby.size()));
            for (var a : nearby) {
                sb.append(String.format("  %s (%s) at (%d,%d) dist=%d: %.0f credits, %s",
                        a.id(), a.identity().archetype(),
                        a.state().location().q(), a.state().location().r(),
                        a.state().location().distanceTo(loc),
                        a.state().credits(), a.state().employmentStatus()));
                // Show what they have for trade potential
                var inv = a.state().inventory();
                if (!inv.isEmpty()) {
                    sb.append(", has: ");
                    inv.entrySet().stream()
                            .filter(e -> e.getValue() > 0)
                            .limit(5)
                            .forEach(e -> sb.append(e.getKey()).append("x").append(e.getValue()).append(" "));
                }
                sb.append("\n");
            }
        } else {
            sb.append("No agents within communication range.\n");
        }

        // Recent messages directed at this agent (private + broadcast at their tile)
        var recentMessages = communicationDao.getAllMessages().stream()
                .filter(m -> m.tick() >= Math.max(0, currentTick - 3)) // last 3 ticks
                .filter(m -> m.channel() == com.measim.model.communication.Message.Channel.AGENT_TO_AGENT
                        || m.channel() == com.measim.model.communication.Message.Channel.BROADCAST)
                .filter(m -> !m.senderId().equals(agent.id())) // not my own messages
                .filter(m -> m.receiverId().equals(agent.id()) // directed at me
                        || "ALL_AT_TILE".equals(m.receiverId())) // or broadcast
                .limit(8)
                .toList();
        if (!recentMessages.isEmpty()) {
            sb.append("Recent messages:\n");
            for (var msg : recentMessages) {
                String prefix = msg.channel() == com.measim.model.communication.Message.Channel.BROADCAST
                        ? "[group]" : "[private]";
                sb.append(String.format("  %s %s (tick %d): %s\n", prefix, msg.senderId(), msg.tick(),
                        msg.content().substring(0, Math.min(120, msg.content().length()))));
            }
        }

        return sb.toString();
    }

    Map<ItemType, Double> buildPriceSnapshot() {
        Map<ItemType, Double> prices = new HashMap<>();
        for (ResourceType r : ResourceType.values()) prices.put(ItemType.of(r), 2.0);
        for (ProductType p : ProductType.values()) prices.put(ItemType.of(p), 10.0);
        for (var market : marketDao.getAllMarkets()) {
            for (ResourceType r : ResourceType.values()) {
                double p = market.getLastPrice(ItemType.of(r));
                if (p > 0) prices.put(ItemType.of(r), p);
            }
            for (ProductType pt : ProductType.values()) {
                double p = market.getLastPrice(ItemType.of(pt));
                if (p > 0) prices.put(ItemType.of(pt), p);
            }
        }
        return prices;
    }
}
