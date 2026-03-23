package com.measim.service.externality;

import com.measim.dao.ExternalityDao;
import com.measim.dao.InfrastructureDao;
import com.measim.dao.WorldDao;
import com.measim.model.communication.Message;
import com.measim.model.externality.Byproduct;
import com.measim.model.externality.ExternalityProfile;
import com.measim.model.infrastructure.Infrastructure;
import com.measim.model.world.HexCoord;
import com.measim.model.world.Tile;
import com.measim.service.communication.CommunicationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ExternalityServiceImpl implements ExternalityService {

    private static final String GM_ID = "GAME_MASTER";

    private final ExternalityDao externalityDao;
    private final InfrastructureDao infraDao;
    private final WorldDao worldDao;
    private final CommunicationService commService;

    @Inject
    public ExternalityServiceImpl(ExternalityDao externalityDao, InfrastructureDao infraDao,
                                   WorldDao worldDao, CommunicationService commService) {
        this.externalityDao = externalityDao;
        this.infraDao = infraDao;
        this.worldDao = worldDao;
        this.commService = commService;
    }

    @Override
    public void processExternalities(int currentTick) {
        for (ExternalityProfile profile : externalityDao.getAllProfiles()) {
            // Get entity state for evolution model
            double usage = 0.5; // default
            int maintGap = 0;
            HexCoord location = null;

            var infraOpt = infraDao.getById(profile.entityId());
            if (infraOpt.isPresent()) {
                Infrastructure infra = infraOpt.get();
                location = infra.location();
                usage = infra.condition(); // higher condition = higher usage capacity
                maintGap = 0; // tracked by infrastructure itself
            }

            double envHealth = 0.8;
            if (location != null) {
                Tile tile = worldDao.getTile(location);
                if (tile != null) envHealth = tile.environment().averageHealth();
            }

            // Process each byproduct
            var outputs = profile.tick(currentTick, usage, maintGap, envHealth);
            for (var output : outputs) {
                if (location == null) continue;

                // Apply TRUE byproduct to the world (regardless of visibility)
                applyByproductToWorld(output.byproduct(), output.amount(), location);

                // Log hidden byproducts that cross detection thresholds
                if (output.measurable() && output.byproduct().visibility() != Byproduct.ByproductVisibility.VISIBLE) {
                    commService.logThought(GM_ID,
                            String.format("EXTERNALITY DETECTED: %s from %s — accumulated %.2f now measurable",
                                    output.byproduct().name(), profile.entityId(), output.accumulated()),
                            Message.Channel.GM_WORLD_NARRATION, currentTick);
                }
            }
        }
    }

    @Override
    public double getMeasuredPollution(String entityId, int currentTick) {
        return externalityDao.getProfile(entityId)
                .map(p -> p.measuredPollution(currentTick, 0.5, 0, 0.8))
                .orElse(0.0);
    }

    @Override
    public void registerProfile(ExternalityProfile profile) {
        externalityDao.registerProfile(profile);
    }

    @Override
    public double getAgentMeasuredPollution(String agentId, int currentTick) {
        double total = 0;
        for (Infrastructure infra : infraDao.getByOwner(agentId)) {
            total += getMeasuredPollution(infra.id(), currentTick);
        }
        return total;
    }

    private void applyByproductToWorld(Byproduct byproduct, double amount, HexCoord location) {
        Tile tile = worldDao.getTile(location);
        if (tile == null) return;

        // Apply to the tile and diffuse to neighbors
        switch (byproduct.type()) {
            case AIR_POLLUTION, CHEMICAL, RADIATION -> {
                tile.environment().applyPollution(amount * 0.3);
                // Diffuse
                for (int r = 1; r <= byproduct.diffusionRadius(); r++) {
                    for (HexCoord neighbor : location.ring(r)) {
                        Tile n = worldDao.getTile(neighbor);
                        if (n != null) n.environment().applyDiffusedPollution(amount * 0.3, r);
                    }
                }
            }
            case WATER_CONTAMINATION -> tile.environment().applyPollution(amount * 0.4);
            case SOIL_DEGRADATION -> tile.environment().applyPollution(amount * 0.5);
            case NOISE, SOCIAL -> {} // affects satisfaction, not environment directly
            case WASTE -> tile.environment().applyPollution(amount * 0.2);
            case THERMAL -> tile.environment().applyPollution(amount * 0.1);
            case ECOLOGICAL -> tile.environment().applyPollution(amount * 0.6);
            case CUSTOM -> tile.environment().applyPollution(amount * 0.3);
        }
    }
}
