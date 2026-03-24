package com.measim.service.simulation.phases;

import com.measim.dao.ContractDao;
import com.measim.dao.PropertyDao;
import com.measim.dao.WorldDao;
import com.measim.model.agent.Agent;
import com.measim.model.agent.AgentState;
import com.measim.model.contract.Contract;
import com.measim.model.economy.ItemType;
import com.measim.model.economy.ProductType;
import com.measim.model.world.Tile;

/**
 * Handles subsistence needs: food and shelter.
 *
 * DESIGN PRINCIPLES:
 * 1. Grace period: first 6 ticks have no penalties (economy bootstrapping)
 * 2. Graduated consequences: small penalties first, escalating over time
 * 3. Multiple ways to meet each need (self-sufficient OR market OR UBI)
 * 4. Incapacitation at very low satisfaction (survival mode, not death)
 * 5. Safety valves: starting credits buy ~100 ticks of food, settlements = shelter
 */
public class SubsistenceHelper {

    private static final int GRACE_PERIOD_TICKS = 6;
    private static final double INCAPACITATION_THRESHOLD = 0.1;

    private final WorldDao worldDao;
    private final PropertyDao propertyDao;
    private final ContractDao contractDao;

    public SubsistenceHelper(WorldDao worldDao, PropertyDao propertyDao, ContractDao contractDao) {
        this.worldDao = worldDao;
        this.propertyDao = propertyDao;
        this.contractDao = contractDao;
    }

    /**
     * Process subsistence needs for an agent. Called every tick.
     * Returns true if agent is incapacitated (can only do survival actions).
     */
    public boolean processNeeds(Agent agent, int currentTick) {
        AgentState state = agent.state();

        // Grace period: no penalties while economy bootstraps
        if (currentTick <= GRACE_PERIOD_TICKS) {
            // Still consume food if available (builds good habits)
            consumeFood(state);
            return false;
        }

        // Food
        boolean fed = consumeFood(state);
        if (!fed) {
            int ticksHungry = countConsecutiveTicksWithout(agent, "fed", currentTick);
            if (ticksHungry <= 3) {
                state.setSatisfaction(state.satisfaction() - 0.03); // mild hunger
            } else if (ticksHungry <= 6) {
                state.setSatisfaction(state.satisfaction() - 0.05); // serious hunger
            } else {
                state.setSatisfaction(state.satisfaction() - 0.08); // starvation
            }
        }

        // Shelter: own a claim, rent a claim, or be on a settlement zone
        boolean sheltered = hasShelter(agent);
        if (!sheltered) {
            int ticksHomeless = countConsecutiveTicksWithout(agent, "sheltered", currentTick);
            if (ticksHomeless <= 6) {
                state.setSatisfaction(state.satisfaction() - 0.02); // uncomfortable
            } else {
                state.setSatisfaction(state.satisfaction() - 0.04); // exposure
            }
        }

        // Optional comforts (no penalty, just bonus)
        ItemType goods = ItemType.of(ProductType.BASIC_GOODS);
        if (state.getInventoryCount(goods) > 0) {
            state.removeFromInventory(goods, 1);
            state.setSatisfaction(state.satisfaction() + 0.01);
        }

        // Medicine: helps recovery when satisfaction is low
        ItemType medicine = ItemType.of(ProductType.MEDICINE);
        if (state.satisfaction() < 0.35 && state.getInventoryCount(medicine) > 0) {
            state.removeFromInventory(medicine, 1);
            state.setSatisfaction(state.satisfaction() + 0.08);
        }

        // Satisfaction naturally drifts toward 0.5 (not infinitely growing)
        if (state.satisfaction() > 0.6 && fed && sheltered) {
            state.setSatisfaction(state.satisfaction() - 0.005);
        }

        // Track status for consecutive-tick counting
        if (fed) agent.addMemory(new com.measim.model.agent.MemoryEntry(
                currentTick, "SUBSISTENCE", "fed", 0.1, null, 0));
        if (sheltered) agent.addMemory(new com.measim.model.agent.MemoryEntry(
                currentTick, "SUBSISTENCE", "sheltered", 0.1, null, 0));

        // Incapacitation check
        return state.satisfaction() <= INCAPACITATION_THRESHOLD;
    }

    /**
     * Is this agent incapacitated? (survival mode only)
     */
    public static boolean isIncapacitated(Agent agent) {
        return agent.state().satisfaction() <= INCAPACITATION_THRESHOLD;
    }

    private boolean consumeFood(AgentState state) {
        ItemType food = ItemType.of(ProductType.FOOD);
        if (state.getInventoryCount(food) > 0) {
            state.removeFromInventory(food, 1);
            state.setSatisfaction(state.satisfaction() + 0.02);
            return true;
        }
        return false;
    }

    private boolean hasShelter(Agent agent) {
        var loc = agent.state().location();

        // On a settlement zone = basic shelter
        Tile tile = worldDao.getTile(loc);
        if (tile != null && tile.isSettlementZone()) return true;

        // Owns a property claim anywhere = has a home
        if (!propertyDao.getClaimsByOwner(agent.id()).isEmpty()) return true;

        // Has a rental contract = renting a home
        for (Contract contract : contractDao.getContractsForAgent(agent.id())) {
            if (contract.isActive() && contract.type() == Contract.ContractType.RENTAL) return true;
        }

        return false;
    }

    /**
     * Count how many consecutive recent ticks the agent was WITHOUT a need met.
     * Uses memory to check — if the last N memories of this type are missing, count up.
     */
    private int countConsecutiveTicksWithout(Agent agent, String needType, int currentTick) {
        var recent = agent.memory().getByType("SUBSISTENCE", 12);
        int consecutive = 0;
        for (int tick = currentTick - 1; tick >= Math.max(1, currentTick - 12); tick--) {
            int t = tick;
            boolean metThisTick = recent.stream()
                    .anyMatch(m -> m.tick() == t && m.description().equals(needType));
            if (!metThisTick) consecutive++;
            else break;
        }
        return consecutive;
    }
}
