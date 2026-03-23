package com.measim.model.agent;

import com.measim.model.economy.ItemType;
import com.measim.model.scoring.ModifierSet;
import com.measim.model.scoring.ScoreVector;
import com.measim.model.world.HexCoord;

import java.util.*;

public class AgentState {

    private double credits;
    private final ScoreVector scoreVector;
    private ModifierSet modifiers;
    private EmploymentStatus employmentStatus;
    private final List<String> ownedBusinessIds;
    private int ownedRobots;
    private final Map<ItemType, Integer> inventory;
    private HexCoord location;
    private double satisfaction;
    private boolean domainTwoCompliance;
    private double totalRevenue;
    private double totalEmissions;
    private int humanEmployees;
    private double commonsScore;

    public AgentState(HexCoord startLocation, double startingCredits) {
        this.credits = startingCredits;
        this.scoreVector = new ScoreVector();
        this.modifiers = ModifierSet.NEUTRAL;
        this.employmentStatus = EmploymentStatus.UNEMPLOYED;
        this.ownedBusinessIds = new ArrayList<>();
        this.ownedRobots = 0;
        this.inventory = new HashMap<>();
        this.location = startLocation;
        this.satisfaction = 0.5;
        this.domainTwoCompliance = true;
        this.totalRevenue = 0;
        this.totalEmissions = 0;
        this.humanEmployees = 0;
        this.commonsScore = 0.5;
    }

    public void addCredits(double amount) { credits += amount; }
    public boolean spendCredits(double amount) {
        if (credits < amount) return false;
        credits -= amount;
        return true;
    }

    public void addToInventory(ItemType type, int quantity) { inventory.merge(type, quantity, Integer::sum); }
    public boolean removeFromInventory(ItemType type, int quantity) {
        int current = inventory.getOrDefault(type, 0);
        if (current < quantity) return false;
        inventory.put(type, current - quantity);
        if (inventory.get(type) <= 0) inventory.remove(type);
        return true;
    }
    public int getInventoryCount(ItemType type) { return inventory.getOrDefault(type, 0); }

    public double credits() { return credits; }
    public ScoreVector scoreVector() { return scoreVector; }
    public ModifierSet modifiers() { return modifiers; }
    public void setModifiers(ModifierSet modifiers) { this.modifiers = modifiers; }
    public EmploymentStatus employmentStatus() { return employmentStatus; }
    public void setEmploymentStatus(EmploymentStatus status) { this.employmentStatus = status; }
    public List<String> ownedBusinessIds() { return ownedBusinessIds; }
    public int ownedRobots() { return ownedRobots; }
    public void setOwnedRobots(int count) { this.ownedRobots = count; }
    public Map<ItemType, Integer> inventory() { return Collections.unmodifiableMap(inventory); }
    public HexCoord location() { return location; }
    public void setLocation(HexCoord location) { this.location = location; }
    public double satisfaction() { return satisfaction; }
    public void setSatisfaction(double satisfaction) { this.satisfaction = Math.max(0, Math.min(1, satisfaction)); }
    public boolean domainTwoCompliance() { return domainTwoCompliance; }
    public void setDomainTwoCompliance(boolean compliance) { this.domainTwoCompliance = compliance; }
    public double totalRevenue() { return totalRevenue; }
    public void addRevenue(double amount) { this.totalRevenue += amount; }
    public double totalEmissions() { return totalEmissions; }
    public void addEmissions(double amount) { this.totalEmissions += amount; }
    public int humanEmployees() { return humanEmployees; }
    public void setHumanEmployees(int count) { this.humanEmployees = count; }
    public double commonsScore() { return commonsScore; }
    public void setCommonsScore(double score) { this.commonsScore = score; }
}
