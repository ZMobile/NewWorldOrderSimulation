package com.measim.service.property;

import com.measim.dao.AgentDao;
import com.measim.dao.PropertyDao;
import com.measim.dao.WorldDao;
import com.measim.model.agent.Agent;
import com.measim.model.property.TileClaim;
import com.measim.model.property.TilePropertyStatus;
import com.measim.model.world.HexCoord;
import com.measim.model.world.Tile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class PropertyServiceImpl implements PropertyService {

    private static final double BASE_CLAIM_PRICE_PER_SLOT = 50.0;

    private final PropertyDao propertyDao;
    private final WorldDao worldDao;
    private final AgentDao agentDao;

    @Inject
    public PropertyServiceImpl(PropertyDao propertyDao, WorldDao worldDao, AgentDao agentDao) {
        this.propertyDao = propertyDao;
        this.worldDao = worldDao;
        this.agentDao = agentDao;
    }

    @Override
    public void initializePropertySystem() {
        // Initialize property status for all passable tiles
        for (Tile tile : worldDao.getAllTiles()) {
            if (tile.terrain().isPassable()) {
                propertyDao.initializeTile(new TilePropertyStatus(tile.coord(), tile.terrain()));
            }
        }
    }

    @Override
    public Optional<TileClaim> purchaseClaim(String agentId, HexCoord tile, int slots, int currentTick) {
        Agent agent = agentDao.getAgent(agentId);
        if (agent == null) return Optional.empty();

        var statusOpt = propertyDao.getTileStatus(tile);
        if (statusOpt.isEmpty() || !statusOpt.get().canClaim(slots)) return Optional.empty();

        double price = getClaimBasePrice(tile) * slots;
        if (!agent.state().spendCredits(price)) return Optional.empty();

        String id = "claim_" + UUID.randomUUID().toString().substring(0, 8);
        TileClaim claim = new TileClaim(id, tile, slots, agentId, price, currentTick);
        propertyDao.addClaim(claim);
        return Optional.of(claim);
    }

    @Override
    public boolean transferClaim(String claimId, String newOwnerId, double salePrice) {
        var claimOpt = propertyDao.getClaim(claimId);
        Agent buyer = agentDao.getAgent(newOwnerId);
        if (claimOpt.isEmpty() || buyer == null) return false;

        TileClaim claim = claimOpt.get();
        if (!buyer.state().spendCredits(salePrice)) return false;

        // Pay seller
        Agent seller = agentDao.getAgent(claim.ownerId());
        if (seller != null) seller.state().addCredits(salePrice);

        claim.transfer(newOwnerId, salePrice);
        return true;
    }

    @Override
    public boolean rentClaim(String claimId, String tenantId) {
        var claimOpt = propertyDao.getClaim(claimId);
        if (claimOpt.isEmpty() || !claimOpt.get().isForRent()) return false;
        claimOpt.get().rentTo(tenantId);
        return true;
    }

    @Override
    public void setForRent(String claimId, double pricePerTick) {
        propertyDao.getClaim(claimId).ifPresent(c -> c.setForRent(pricePerTick));
    }

    @Override
    public void processRentPayments(int currentTick) {
        for (TileClaim claim : propertyDao.getAvailableForRent()) {
            // Already rented claims — not "available" for rent
        }
        // Process all rented claims
        for (var claim : propertyDao.getClaimsByOwner("*")) {
            // This won't work with "*" — need a different approach
        }
        // Process all claims that have tenants
        for (Tile tile : worldDao.getAllTiles()) {
            for (TileClaim claim : propertyDao.getClaimsOnTile(tile.coord())) {
                if (claim.isRented() && claim.rentalPrice() > 0) {
                    Agent tenant = agentDao.getAgent(claim.tenantId());
                    Agent owner = agentDao.getAgent(claim.ownerId());
                    if (tenant != null && owner != null) {
                        if (tenant.state().spendCredits(claim.rentalPrice())) {
                            owner.state().addCredits(claim.rentalPrice());
                            owner.state().addRevenue(claim.rentalPrice());
                        } else {
                            // Can't pay rent — evict
                            claim.endRental();
                        }
                    }
                }
            }
        }
    }

    @Override
    public int availableSlots(HexCoord tile) {
        return propertyDao.getTileStatus(tile).map(TilePropertyStatus::availableSlots).orElse(0);
    }

    @Override
    public List<TileClaim> getAgentProperties(String agentId) {
        return propertyDao.getClaimsByOwner(agentId);
    }

    @Override
    public List<TileClaim> getClaimsOnTile(HexCoord tile) {
        return propertyDao.getClaimsOnTile(tile);
    }

    @Override
    public double getClaimBasePrice(HexCoord tile) {
        Tile t = worldDao.getTile(tile);
        if (t == null) return BASE_CLAIM_PRICE_PER_SLOT;
        // Price scales with resources and settlement status
        double resourceBonus = t.resources().size() * 10;
        double settlementBonus = t.isSettlementZone() ? 30 : 0;
        return BASE_CLAIM_PRICE_PER_SLOT + resourceBonus + settlementBonus;
    }
}
