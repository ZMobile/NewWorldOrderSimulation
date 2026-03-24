package com.measim.model.property;

import com.measim.model.world.HexCoord;

/**
 * A claim to slots on a tile. Agents own claims, not entire tiles.
 * A tile's total capacity is set by terrain type. Claims consume slots.
 *
 * Claims can be:
 *   - Purchased from government (initial claim — price set by governance)
 *   - Purchased from another agent (resale — market price)
 *   - Rented from another agent (tenant pays periodic rent)
 *   - Used to build infrastructure or operate services
 *   - Left empty (speculative holding — still pays property tax if governance sets one)
 */
public class TileClaim {

    private final String id;
    private final HexCoord tile;
    private final int slotCount;
    private String ownerId;
    private String tenantId;        // null if owner is using it, or if empty
    private double purchasePrice;   // what the owner paid
    private double rentalPrice;     // per-tick rental if rented out (0 = not for rent)
    private final int acquiredTick;
    private String builtInfrastructureId;  // what's built on this claim (null if empty)
    private String operatingServiceId;      // service running on this claim (null if empty)

    public TileClaim(String id, HexCoord tile, int slotCount, String ownerId,
                     double purchasePrice, int acquiredTick) {
        this.id = id;
        this.tile = tile;
        this.slotCount = slotCount;
        this.ownerId = ownerId;
        this.purchasePrice = purchasePrice;
        this.acquiredTick = acquiredTick;
        this.rentalPrice = 0;
    }

    public boolean isEmpty() { return builtInfrastructureId == null && operatingServiceId == null; }
    public boolean isRented() { return tenantId != null; }
    public boolean isForRent() { return rentalPrice > 0 && tenantId == null && isEmpty(); }

    public void transfer(String newOwnerId, double salePrice) {
        this.ownerId = newOwnerId;
        this.purchasePrice = salePrice;
        this.tenantId = null;
        this.rentalPrice = 0;
    }

    public void setForRent(double pricePerTick) { this.rentalPrice = pricePerTick; }
    public void rentTo(String tenantId) { this.tenantId = tenantId; }
    public void endRental() { this.tenantId = null; }

    public void buildOn(String infrastructureId) { this.builtInfrastructureId = infrastructureId; }
    public void operateServiceOn(String serviceId) { this.operatingServiceId = serviceId; }
    public void clearBuilding() { this.builtInfrastructureId = null; }
    public void clearService() { this.operatingServiceId = null; }

    public String id() { return id; }
    public HexCoord tile() { return tile; }
    public int slotCount() { return slotCount; }
    public String ownerId() { return ownerId; }
    public String tenantId() { return tenantId; }
    public double purchasePrice() { return purchasePrice; }
    public double rentalPrice() { return rentalPrice; }
    public int acquiredTick() { return acquiredTick; }
    public String builtInfrastructureId() { return builtInfrastructureId; }
    public String operatingServiceId() { return operatingServiceId; }
    public String effectiveUserId() { return tenantId != null ? tenantId : ownerId; }
}
