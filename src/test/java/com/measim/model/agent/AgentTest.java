package com.measim.model.agent;

import com.measim.model.economy.ItemType;
import com.measim.model.economy.ProductType;
import com.measim.model.world.HexCoord;
import org.junit.jupiter.api.Test;
import java.util.Random;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AgentTest {

    @Test void agentCreation() {
        IdentityProfile profile = IdentityProfile.fromArchetype("test1", "Test", Archetype.ENTREPRENEUR, Set.of("general"), new Random(42));
        AgentState state = new AgentState(new HexCoord(5, 5), 1000.0);
        Agent agent = new Agent(profile, state, 100);
        assertEquals("test1", agent.id());
        assertEquals(Archetype.ENTREPRENEUR, profile.archetype());
        assertEquals(1000.0, state.credits());
    }

    @Test void inventoryOperations() {
        AgentState state = new AgentState(new HexCoord(0, 0), 500);
        ItemType food = ItemType.of(ProductType.FOOD);
        state.addToInventory(food, 10);
        assertEquals(10, state.getInventoryCount(food));
        assertTrue(state.removeFromInventory(food, 3));
        assertEquals(7, state.getInventoryCount(food));
        assertFalse(state.removeFromInventory(food, 100));
        assertEquals(7, state.getInventoryCount(food));
    }

    @Test void creditOperations() {
        AgentState state = new AgentState(new HexCoord(0, 0), 1000);
        assertTrue(state.spendCredits(300));
        assertEquals(700, state.credits());
        assertFalse(state.spendCredits(800));
        assertEquals(700, state.credits());
        state.addCredits(100);
        assertEquals(800, state.credits());
    }

    @Test void memoryStream() {
        Agent agent = new Agent(IdentityProfile.fromArchetype("a", "A", Archetype.COOPERATOR, Set.of(), new Random(1)),
                new AgentState(new HexCoord(0, 0), 100), 50);
        agent.addMemory(new MemoryEntry(1, "OBSERVATION", "Saw resource", 0.5, null, 0));
        agent.addMemory(new MemoryEntry(2, "ACTION", "Produced food", 0.8, null, 10));
        assertEquals(2, agent.memory().size());
        assertEquals(1, agent.memory().getByType("ACTION", 5).size());
    }
}
