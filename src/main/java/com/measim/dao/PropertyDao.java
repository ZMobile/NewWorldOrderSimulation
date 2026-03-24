package com.measim.dao;

import com.measim.model.property.TileClaim;
import com.measim.model.property.TilePropertyStatus;
import com.measim.model.world.HexCoord;

import java.util.List;
import java.util.Optional;

public interface PropertyDao {
    void initializeTile(TilePropertyStatus status);
    Optional<TilePropertyStatus> getTileStatus(HexCoord tile);
    void addClaim(TileClaim claim);
    Optional<TileClaim> getClaim(String claimId);
    List<TileClaim> getClaimsByOwner(String agentId);
    List<TileClaim> getClaimsOnTile(HexCoord tile);
    List<TileClaim> getAvailableForRent();
    List<TileClaim> getForSale();
}
