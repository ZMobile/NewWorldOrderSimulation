package com.measim.model.infrastructure;

import com.measim.model.world.HexCoord;

/**
 * A placed infrastructure instance in the world.
 * The type defines what it does; this tracks where it is and its current state.
 */
public class Infrastructure {

    private final String id;
    private final InfrastructureType type;
    private final String ownerId;
    private final HexCoord location;       // where it's built
    private final HexCoord connectedTo;    // source tile for POINT_TO_POINT, null for AREA/LOCAL
    private final int builtTick;
    private double condition;              // 1.0 = perfect, degrades without maintenance
    private boolean active;

    public Infrastructure(String id, InfrastructureType type, String ownerId,
                          HexCoord location, HexCoord connectedTo, int builtTick) {
        this.id = id;
        this.type = type;
        this.ownerId = ownerId;
        this.location = location;
        this.connectedTo = connectedTo;
        this.builtTick = builtTick;
        this.condition = 1.0;
        this.active = true;
    }

    /**
     * Pay maintenance to keep infrastructure running.
     * Returns the cost. If not paid, condition degrades.
     */
    public double tickMaintenance(boolean paid) {
        if (!active) return 0;
        if (paid) {
            condition = Math.min(1.0, condition + 0.01); // slow repair if maintained
            return type.maintenanceCostPerTick();
        } else {
            condition -= 0.05; // degrades without maintenance
            if (condition <= 0) {
                active = false;
                condition = 0;
            }
            return 0;
        }
    }

    /**
     * Effective magnitude of effects, scaled by condition.
     */
    public double effectiveMagnitude(InfrastructureEffect effect) {
        return effect.magnitude() * condition;
    }

    public boolean isActive() { return active && condition > 0; }
    public boolean isPointToPoint() { return type.connectionMode() == InfrastructureType.ConnectionMode.POINT_TO_POINT; }
    public boolean isAreaOfEffect() { return type.connectionMode() == InfrastructureType.ConnectionMode.AREA_OF_EFFECT; }

    public String id() { return id; }
    public InfrastructureType type() { return type; }
    public String ownerId() { return ownerId; }
    public HexCoord location() { return location; }
    public HexCoord connectedTo() { return connectedTo; }
    public int builtTick() { return builtTick; }
    public double condition() { return condition; }
}
