package com.measim.service.trade;

import com.measim.dao.InfrastructureDao;
import com.measim.model.agent.Agent;
import com.measim.model.infrastructure.Infrastructure;
import com.measim.model.infrastructure.InfrastructureEffect;
import com.measim.model.world.HexCoord;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Computes effective communication range for agents based on nearby infrastructure.
 *
 * Default: 2 tiles (shouting distance). Infrastructure with COMMUNICATION_RANGE effect
 * extends this. The GM assigns this effect when evaluating infrastructure proposals
 * (message boards, postal services, radio towers, telecom networks).
 */
@Singleton
public class CommunicationRangeServiceImpl implements CommunicationRangeService {

    private final InfrastructureDao infraDao;

    @Inject
    public CommunicationRangeServiceImpl(InfrastructureDao infraDao) {
        this.infraDao = infraDao;
    }

    @Override
    public int getEffectiveRange(Agent agent) {
        HexCoord loc = agent.state().location();
        int maxRange = BASE_RANGE;

        // Check infrastructure at and near agent's tile for comm range effects
        for (Infrastructure infra : infraDao.getAtTile(loc)) {
            maxRange = Math.max(maxRange, getCommRangeFromInfra(infra));
        }

        // Also check infrastructure connected TO this tile (e.g., road network)
        for (Infrastructure infra : infraDao.getConnectionsTo(loc)) {
            maxRange = Math.max(maxRange, getCommRangeFromInfra(infra));
        }

        return maxRange;
    }

    @Override
    public boolean canCommunicate(Agent a, Agent b) {
        int distance = a.state().location().distanceTo(b.state().location());
        // Either agent being in range is sufficient (if A has a radio, B can hear it)
        return distance <= getEffectiveRange(a) || distance <= getEffectiveRange(b);
    }

    private int getCommRangeFromInfra(Infrastructure infra) {
        if (infra.condition() <= 0.05) return 0; // broken infrastructure doesn't work

        for (var effect : infra.type().effects()) {
            if (effect.type() == InfrastructureEffect.EffectType.COMMUNICATION_RANGE) {
                // magnitude is the range in tiles, scaled by condition
                return (int) (effect.magnitude() * infra.condition());
            }
        }
        return 0;
    }
}
