package com.measim.model.externality;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The full externality profile of an entity — all byproducts it produces.
 * Mirrors RiskProfile: true byproducts (GM-set, world uses) vs what's measurable.
 *
 * TRUE profile: what actually happens. Applied to tiles, environment, agents.
 * MEASURED profile: what the EF scoring axis can detect. Drives MERIT modifiers.
 * PERCEIVED profile: what agents think. Drives their decisions.
 *
 * Three-layer knowledge gap:
 *   Reality → Measurement → Perception
 *   (each can differ from the previous)
 */
public class ExternalityProfile {

    private final String entityId;
    private final String entityType;
    private final int creationTick;
    private final List<Byproduct> trueByproducts;
    private final List<AccumulatedByproduct> accumulations;

    public ExternalityProfile(String entityId, String entityType, int creationTick) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.creationTick = creationTick;
        this.trueByproducts = new ArrayList<>();
        this.accumulations = new ArrayList<>();
    }

    public void addByproduct(Byproduct byproduct) {
        trueByproducts.add(byproduct);
        accumulations.add(new AccumulatedByproduct(byproduct.id(), 0));
    }

    /**
     * Tick: compute actual outputs, accumulate, return what should be applied to the world.
     */
    public List<ByproductOutput> tick(int currentTick, double usageIntensity, int ticksSinceMaintenance, double envHealth) {
        int age = currentTick - creationTick;
        List<ByproductOutput> outputs = new ArrayList<>();

        for (int i = 0; i < trueByproducts.size(); i++) {
            Byproduct bp = trueByproducts.get(i);
            double amount = bp.actualOutput(age, usageIntensity, ticksSinceMaintenance, envHealth);

            // Accumulate
            AccumulatedByproduct acc = accumulations.get(i);
            double newAccumulated = acc.accumulated + amount * bp.accumulationRate();
            accumulations.set(i, new AccumulatedByproduct(acc.byproductId, newAccumulated));

            // Is this detectable by the measurement system?
            boolean measurable = bp.isDetectable(age, newAccumulated);

            outputs.add(new ByproductOutput(bp, amount, newAccumulated, measurable));
        }
        return outputs;
    }

    /**
     * Get only the byproducts that the measurement system can currently detect.
     * This is what feeds into EF scoring.
     */
    public double measuredPollution(int currentTick, double usageIntensity,
                                     int ticksSinceMaintenance, double envHealth) {
        return tick(currentTick, usageIntensity, ticksSinceMaintenance, envHealth).stream()
                .filter(ByproductOutput::measurable)
                .mapToDouble(ByproductOutput::amount)
                .sum();
    }

    /**
     * Get the TRUE total pollution (including hidden/undetected).
     * This is what actually affects the world.
     */
    public double truePollution(int currentTick, double usageIntensity,
                                 int ticksSinceMaintenance, double envHealth) {
        return tick(currentTick, usageIntensity, ticksSinceMaintenance, envHealth).stream()
                .mapToDouble(ByproductOutput::amount)
                .sum();
    }

    public String entityId() { return entityId; }
    public List<Byproduct> trueByproducts() { return Collections.unmodifiableList(trueByproducts); }

    public record ByproductOutput(Byproduct byproduct, double amount, double accumulated, boolean measurable) {}
    private record AccumulatedByproduct(String byproductId, double accumulated) {}
}
