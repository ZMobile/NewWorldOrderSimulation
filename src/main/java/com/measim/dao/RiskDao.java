package com.measim.dao;

import com.measim.model.risk.PerceivedRisk;
import com.measim.model.risk.RiskEvent;
import com.measim.model.risk.RiskProfile;

import java.util.List;
import java.util.Optional;

public interface RiskDao {
    void registerProfile(RiskProfile profile);
    Optional<RiskProfile> getProfile(String entityId);
    List<RiskProfile> getAllProfiles();
    List<RiskProfile> getProfilesByType(RiskProfile.EntityType type);

    void recordEvent(RiskEvent event);
    List<RiskEvent> getEventsForEntity(String entityId);
    List<RiskEvent> getEventsForTick(int tick);
    List<RiskEvent> getRecentEvents(int lastNTicks, int currentTick);
    List<RiskEvent> getAllEvents();

    // Perceived risk (what agents think)
    void recordPerceivedRisk(PerceivedRisk perceived);
    List<PerceivedRisk> getPerceivedRisksForAgent(String agentId);
    Optional<PerceivedRisk> getAgentPerceptionOf(String agentId, String entityId);
}
