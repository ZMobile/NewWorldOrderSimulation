package com.measim.dao;

import com.measim.model.property.TileClaim;
import com.measim.model.property.TilePropertyStatus;
import com.measim.model.world.HexCoord;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class PropertyDaoImpl implements PropertyDao {

    private final Map<HexCoord, TilePropertyStatus> tileStatuses = new HashMap<>();
    private final Map<String, TileClaim> claims = new LinkedHashMap<>();

    @Override
    public void initializeTile(TilePropertyStatus status) { tileStatuses.put(status.tile(), status); }

    @Override
    public Optional<TilePropertyStatus> getTileStatus(HexCoord tile) {
        return Optional.ofNullable(tileStatuses.get(tile));
    }

    @Override
    public void addClaim(TileClaim claim) {
        claims.put(claim.id(), claim);
        tileStatuses.computeIfPresent(claim.tile(), (k, status) -> { status.addClaim(claim); return status; });
    }

    @Override public Optional<TileClaim> getClaim(String claimId) { return Optional.ofNullable(claims.get(claimId)); }

    @Override public List<TileClaim> getClaimsByOwner(String agentId) {
        return claims.values().stream().filter(c -> c.ownerId().equals(agentId)).toList();
    }

    @Override public List<TileClaim> getClaimsOnTile(HexCoord tile) {
        return tileStatuses.containsKey(tile) ? tileStatuses.get(tile).getClaims() : List.of();
    }

    @Override public List<TileClaim> getAvailableForRent() {
        return claims.values().stream().filter(TileClaim::isForRent).toList();
    }

    @Override public List<TileClaim> getForSale() {
        // Claims marked for sale (rental price used as sale price marker when negative — convention)
        return claims.values().stream().filter(c -> c.isEmpty() && !c.isRented()).toList();
    }
}
