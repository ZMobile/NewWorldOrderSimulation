package com.measim.model.property;

import com.measim.model.infrastructure.InfrastructureConstraints;
import com.measim.model.world.HexCoord;
import com.measim.model.world.TerrainType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks all claims on a single tile. Enforces capacity limits.
 */
public class TilePropertyStatus {

    private final HexCoord tile;
    private final int totalCapacity;
    private final List<TileClaim> claims;

    public TilePropertyStatus(HexCoord tile, TerrainType terrain) {
        this.tile = tile;
        this.totalCapacity = InfrastructureConstraints.terrainCapacity(terrain);
        this.claims = new ArrayList<>();
    }

    public int usedSlots() {
        return claims.stream().mapToInt(TileClaim::slotCount).sum();
    }

    public int availableSlots() {
        return totalCapacity - usedSlots();
    }

    public boolean canClaim(int slotCount) {
        return availableSlots() >= slotCount;
    }

    public void addClaim(TileClaim claim) {
        if (canClaim(claim.slotCount())) {
            claims.add(claim);
        }
    }

    public void removeClaim(String claimId) {
        claims.removeIf(c -> c.id().equals(claimId));
    }

    public List<TileClaim> getClaims() { return Collections.unmodifiableList(claims); }
    public List<TileClaim> getClaimsForOwner(String ownerId) {
        return claims.stream().filter(c -> c.ownerId().equals(ownerId)).toList();
    }
    public List<TileClaim> getAvailableForRent() {
        return claims.stream().filter(TileClaim::isForRent).toList();
    }
    public List<TileClaim> getEmptyClaims() {
        return claims.stream().filter(TileClaim::isEmpty).toList();
    }

    public HexCoord tile() { return tile; }
    public int totalCapacity() { return totalCapacity; }
}
