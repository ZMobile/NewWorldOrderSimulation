package com.measim.service.property;

import com.measim.model.property.TileClaim;
import com.measim.model.world.HexCoord;

import java.util.List;
import java.util.Optional;

public interface PropertyService {
    void initializePropertySystem();
    Optional<TileClaim> purchaseClaim(String agentId, HexCoord tile, int slots, int currentTick);
    boolean transferClaim(String claimId, String newOwnerId, double salePrice);
    boolean rentClaim(String claimId, String tenantId);
    void setForRent(String claimId, double pricePerTick);
    void processRentPayments(int currentTick);
    int availableSlots(HexCoord tile);
    List<TileClaim> getAgentProperties(String agentId);
    double getClaimBasePrice(HexCoord tile);
}
