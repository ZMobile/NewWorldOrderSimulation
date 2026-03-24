package com.measim.model.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks what has happened on a tile over time.
 * The GM uses this to make contextual decisions about tile-specific corrections.
 *
 * Examples of what history enables:
 * - "This tile has been heavily industrialized for 30 ticks → soil should be worse"
 * - "This tile was abandoned 20 ticks ago → resources should be regenerating"
 * - "Three risk events have occurred here → this area is becoming dangerous"
 * - "Heavy foot traffic from agents → paths forming, terrain wear"
 */
public class TileHistory {

    private int totalInfrastructureTicksBuilt;  // cumulative ticks with infrastructure
    private int totalAgentVisitTicks;           // how many agent-ticks on this tile
    private int totalProductionTicks;           // ticks where production happened
    private double totalPollutionReceived;      // cumulative pollution applied
    private int riskEventsOccurred;             // count of risk events on this tile
    private int ticksSinceLastActivity;         // how long since anything happened here
    private final List<String> significantEvents;  // brief log of notable events

    public TileHistory() {
        this.significantEvents = new ArrayList<>();
    }

    public void recordInfrastructureTick() { totalInfrastructureTicksBuilt++; ticksSinceLastActivity = 0; }
    public void recordAgentVisit() { totalAgentVisitTicks++; ticksSinceLastActivity = 0; }
    public void recordProduction() { totalProductionTicks++; ticksSinceLastActivity = 0; }
    public void recordPollution(double amount) { totalPollutionReceived += amount; }
    public void recordRiskEvent(String description) {
        riskEventsOccurred++;
        significantEvents.add(description);
        if (significantEvents.size() > 10) significantEvents.removeFirst();
        ticksSinceLastActivity = 0;
    }
    public void recordSignificantEvent(String description) {
        significantEvents.add(description);
        if (significantEvents.size() > 10) significantEvents.removeFirst();
    }
    public void tickIdle() { ticksSinceLastActivity++; }

    /**
     * Summary for GM context. Concise history of this tile.
     */
    public String summary() {
        if (totalAgentVisitTicks == 0 && totalInfrastructureTicksBuilt == 0) {
            return "Untouched wilderness. No agent activity.";
        }
        StringBuilder sb = new StringBuilder();
        if (totalInfrastructureTicksBuilt > 0)
            sb.append("Infrastructure: ").append(totalInfrastructureTicksBuilt).append(" ticks. ");
        if (totalProductionTicks > 0)
            sb.append("Production: ").append(totalProductionTicks).append(" ticks. ");
        if (totalAgentVisitTicks > 0)
            sb.append("Agent visits: ").append(totalAgentVisitTicks).append(". ");
        if (totalPollutionReceived > 0.1)
            sb.append(String.format("Cumulative pollution: %.1f. ", totalPollutionReceived));
        if (riskEventsOccurred > 0)
            sb.append("Risk events: ").append(riskEventsOccurred).append(". ");
        if (ticksSinceLastActivity > 12)
            sb.append("Idle for ").append(ticksSinceLastActivity).append(" ticks. ");
        if (!significantEvents.isEmpty())
            sb.append("Events: ").append(String.join("; ", significantEvents.subList(
                    Math.max(0, significantEvents.size() - 3), significantEvents.size())));
        return sb.toString();
    }

    public int totalInfrastructureTicksBuilt() { return totalInfrastructureTicksBuilt; }
    public int totalAgentVisitTicks() { return totalAgentVisitTicks; }
    public int totalProductionTicks() { return totalProductionTicks; }
    public double totalPollutionReceived() { return totalPollutionReceived; }
    public int riskEventsOccurred() { return riskEventsOccurred; }
    public int ticksSinceLastActivity() { return ticksSinceLastActivity; }
    public List<String> significantEvents() { return Collections.unmodifiableList(significantEvents); }
}
