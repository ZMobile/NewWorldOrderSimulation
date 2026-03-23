package com.measim.model.service;

import com.measim.model.world.HexCoord;

import java.util.ArrayList;
import java.util.List;

/**
 * A running instance of a service in the simulation.
 * Owned and operated by an agent. Consumers can use it each tick.
 */
public class ServiceInstance {

    private final String id;
    private final AgentServiceType type;
    private final String ownerId;
    private final HexCoord location;
    private final int startTick;
    private double reputation;
    private int totalUses;
    private boolean active;
    private final List<String> currentSubscribers;
    private double accumulatedRevenue;
    private double accumulatedCosts;

    public ServiceInstance(String id, AgentServiceType type, String ownerId,
                           HexCoord location, int startTick) {
        this.id = id;
        this.type = type;
        this.ownerId = ownerId;
        this.location = location;
        this.startTick = startTick;
        this.reputation = 0.5;
        this.totalUses = 0;
        this.active = true;
        this.currentSubscribers = new ArrayList<>();
        this.accumulatedRevenue = 0;
        this.accumulatedCosts = 0;
    }

    public boolean hasCapacity() {
        return currentSubscribers.size() < type.capacityPerTick();
    }

    public void addSubscriber(String agentId) {
        if (hasCapacity()) currentSubscribers.add(agentId);
    }

    public void clearSubscribersForTick() {
        currentSubscribers.clear();
    }

    public void recordUse(double revenue, double cost) {
        totalUses++;
        accumulatedRevenue += revenue;
        accumulatedCosts += cost;
    }

    public void updateReputation(double delta) {
        reputation = Math.max(0, Math.min(1.0, reputation + delta));
    }

    public double effectiveQuality() {
        return type.qualityScore() * reputation;
    }

    public int ageTicks(int currentTick) { return currentTick - startTick; }
    public void deactivate() { this.active = false; }

    public String id() { return id; }
    public AgentServiceType type() { return type; }
    public String ownerId() { return ownerId; }
    public HexCoord location() { return location; }
    public int startTick() { return startTick; }
    public double reputation() { return reputation; }
    public int totalUses() { return totalUses; }
    public boolean isActive() { return active; }
    public List<String> currentSubscribers() { return currentSubscribers; }
    public double accumulatedRevenue() { return accumulatedRevenue; }
    public double accumulatedCosts() { return accumulatedCosts; }
    public double profit() { return accumulatedRevenue - accumulatedCosts; }
}
