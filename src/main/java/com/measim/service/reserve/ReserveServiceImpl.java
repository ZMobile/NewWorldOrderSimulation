package com.measim.service.reserve;

import com.measim.dao.AgentDao;
import com.measim.model.communication.Message;
import com.measim.model.config.SimulationConfig;
import com.measim.model.economy.CommodityReserve;
import com.measim.service.communication.CommunicationService;
import com.measim.service.gamemaster.GameMasterPrompts;
import com.measim.service.llm.LlmService;
import com.measim.model.llm.LlmResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Manages the commodity reserve backing the credit system.
 * The GM (Opus) evaluates yearly whether to adjust reserve holdings.
 */
@Singleton
public class ReserveServiceImpl implements ReserveService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GM_ID = "GAME_MASTER";
    private static final double DEFAULT_MINIMUM_RATIO = 0.20; // 20% backing

    private final CommodityReserve reserve;
    private final AgentDao agentDao;
    private final LlmService llmService;
    private final CommunicationService commService;
    private final SimulationConfig config;

    @Inject
    public ReserveServiceImpl(AgentDao agentDao, LlmService llmService,
                               CommunicationService commService, SimulationConfig config) {
        this.reserve = new CommodityReserve(DEFAULT_MINIMUM_RATIO);
        this.agentDao = agentDao;
        this.llmService = llmService;
        this.commService = commService;
        this.config = config;
    }

    @Override
    public void initializeReserve() {
        // Set initial valuations for base commodities
        reserve.setValuation("MINERAL", 5.0);
        reserve.setValuation("ENERGY", 3.0);
        reserve.setValuation("TIMBER", 2.0);
        reserve.setValuation("FOOD_LAND", 2.0);
        reserve.setValuation("WATER_RESOURCE", 1.5);

        // Seed the reserve with initial holdings
        // Total starting credits = agentCount × startingCredits = 200 × 1000 = 200,000
        // Reserve needs to cover 20% = 40,000 in commodity value
        double targetValue = config.agentCount() * config.startingCredits() * DEFAULT_MINIMUM_RATIO;

        // Distribute across commodities
        reserve.deposit("MINERAL", targetValue * 0.3 / 5.0, 0, 0, "Initial reserve seeding");
        reserve.deposit("ENERGY", targetValue * 0.25 / 3.0, 0, 0, "Initial reserve seeding");
        reserve.deposit("TIMBER", targetValue * 0.15 / 2.0, 0, 0, "Initial reserve seeding");
        reserve.deposit("FOOD_LAND", targetValue * 0.15 / 2.0, 0, 0, "Initial reserve seeding");
        reserve.deposit("WATER_RESOURCE", targetValue * 0.15 / 1.5, 0, 0, "Initial reserve seeding");

        commService.logThought(GM_ID,
                String.format("Reserve initialized: %.0f total value, ratio %.1f%% of %.0f credits",
                        reserve.totalValue(), reserve.currentRatio(config.agentCount() * config.startingCredits()) * 100,
                        (double) config.agentCount() * config.startingCredits()),
                Message.Channel.GM_INTERNAL, 0);
    }

    @Override
    public void gmManageReserve(int currentTick) {
        double totalCredits = agentDao.getAllAgents().stream()
                .mapToDouble(a -> a.state().credits()).sum();
        double ratio = reserve.currentRatio(totalCredits);

        commService.logThought(GM_ID,
                String.format("Reserve check: ratio=%.1f%% (minimum %.1f%%), value=%.0f, credits in circulation=%.0f",
                        ratio * 100, reserve.minimumRatio() * 100, reserve.totalValue(), totalCredits),
                Message.Channel.GM_INTERNAL, currentTick);

        if (!llmService.isAvailable()) {
            gmManageReserveDeterministic(currentTick, totalCredits, ratio);
            return;
        }

        try {
            // GOVERNANCE GM — reserve management
            String systemPrompt = GameMasterPrompts.governanceReserveSystemPrompt(
                    String.format("%.0f", reserve.minimumRatio() * 100));

            String userPrompt = String.format("""
                    Current reserve state:
                      Total value: %.0f credits
                      Credits in circulation: %.0f
                      Reserve ratio: %.1f%% (minimum: %.0f%%)
                      Holdings: %s
                      Valuations: %s
                      Recent transactions: %d total
                    """,
                    reserve.totalValue(), totalCredits,
                    ratio * 100, reserve.minimumRatio() * 100,
                    reserve.holdings().toString(), reserve.valuations().toString(),
                    reserve.transactionLog().size());

            LlmResponse response = llmService.queryGameMasterWithModel(
                    config.complexModel(), systemPrompt, userPrompt).join();

            commService.logThought(GM_ID, "Reserve decision: " + response.content(),
                    Message.Channel.GM_INTERNAL, currentTick);

            parseAndApplyReserveDecision(response.content(), currentTick);
        } catch (Exception e) {
            gmManageReserveDeterministic(currentTick, totalCredits, ratio);
        }
    }

    private void gmManageReserveDeterministic(int currentTick, double totalCredits, double ratio) {
        if (ratio < reserve.minimumRatio() * 1.1) {
            // Below comfortable range — buy more commodities
            double deficit = (reserve.minimumRatio() * 1.2 * totalCredits) - reserve.totalValue();
            if (deficit > 0) {
                double mineralToBuy = deficit * 0.5 / reserve.getValuation("MINERAL");
                reserve.deposit("MINERAL", mineralToBuy, deficit * 0.5, currentTick,
                        "Deterministic: ratio below minimum, buying to restore");
                commService.logThought(GM_ID,
                        String.format("Reserve: bought %.0f MINERAL to restore ratio (was %.1f%%)",
                                mineralToBuy, ratio * 100),
                        Message.Channel.GM_INTERNAL, currentTick);
            }
        }
    }

    private void parseAndApplyReserveDecision(String content, int currentTick) {
        try {
            String json = content.trim();
            if (json.startsWith("```")) json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode root = MAPPER.readTree(json);

            String action = root.path("action").asText("HOLD");

            // Apply valuation changes
            JsonNode valuations = root.path("valuationChanges");
            if (valuations.isArray()) {
                for (JsonNode v : valuations) {
                    String commodity = v.path("commodity").asText();
                    double newValue = v.path("newValue").asDouble(0);
                    if (!commodity.isEmpty() && newValue > 0) {
                        reserve.setValuation(commodity, newValue);
                    }
                }
            }

            // Apply trades
            JsonNode trades = root.path("trades");
            if (trades.isArray()) {
                for (JsonNode t : trades) {
                    String commodity = t.path("commodity").asText();
                    double quantity = t.path("quantity").asDouble(0);
                    double creditAmount = t.path("creditAmount").asDouble(0);
                    if (commodity.isEmpty() || quantity <= 0) continue;

                    if ("BUY".equals(action)) {
                        reserve.deposit(commodity, quantity, creditAmount, currentTick,
                                root.path("reasoning").asText("GM trade"));
                    } else if ("SELL".equals(action)) {
                        reserve.withdraw(commodity, quantity, creditAmount, currentTick,
                                root.path("reasoning").asText("GM trade"));
                    }
                }
            }
        } catch (Exception e) {
            // Failed to parse — no action taken, reserve unchanged
        }
    }

    @Override
    public double reserveRatio() {
        double totalCredits = agentDao.getAllAgents().stream()
                .mapToDouble(a -> a.state().credits()).sum();
        return reserve.currentRatio(totalCredits);
    }

    @Override
    public double reserveValue() { return reserve.totalValue(); }

    @Override
    public CommodityReserve getReserve() { return reserve; }
}
