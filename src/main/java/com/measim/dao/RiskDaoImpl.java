package com.measim.dao;

import com.measim.model.risk.PerceivedRisk;
import com.measim.model.risk.RiskEvent;
import com.measim.model.risk.RiskProfile;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class RiskDaoImpl implements RiskDao {

    private final Map<String, RiskProfile> profiles = new LinkedHashMap<>();
    private final List<RiskEvent> events = new ArrayList<>();
    private final List<PerceivedRisk> perceivedRisks = new ArrayList<>();

    @Override public void registerProfile(RiskProfile profile) { profiles.put(profile.entityId(), profile); }
    @Override public Optional<RiskProfile> getProfile(String entityId) { return Optional.ofNullable(profiles.get(entityId)); }
    @Override public List<RiskProfile> getAllProfiles() { return List.copyOf(profiles.values()); }
    @Override public List<RiskProfile> getProfilesByType(RiskProfile.EntityType type) {
        return profiles.values().stream().filter(p -> p.entityType() == type).toList();
    }

    @Override public void recordEvent(RiskEvent event) { events.add(event); }
    @Override public List<RiskEvent> getEventsForEntity(String entityId) {
        return events.stream().filter(e -> e.entityId().equals(entityId)).toList();
    }
    @Override public List<RiskEvent> getEventsForTick(int tick) {
        return events.stream().filter(e -> e.tick() == tick).toList();
    }
    @Override public List<RiskEvent> getRecentEvents(int lastNTicks, int currentTick) {
        return events.stream().filter(e -> e.tick() >= currentTick - lastNTicks).toList();
    }
    @Override public List<RiskEvent> getAllEvents() { return Collections.unmodifiableList(events); }

    @Override public void recordPerceivedRisk(PerceivedRisk perceived) {
        perceivedRisks.removeIf(p -> p.perceiverAgentId().equals(perceived.perceiverAgentId())
                && p.entityId().equals(perceived.entityId()) && p.riskId().equals(perceived.riskId()));
        perceivedRisks.add(perceived);
    }
    @Override public List<PerceivedRisk> getPerceivedRisksForAgent(String agentId) {
        return perceivedRisks.stream().filter(p -> p.perceiverAgentId().equals(agentId)).toList();
    }
    @Override public Optional<PerceivedRisk> getAgentPerceptionOf(String agentId, String entityId) {
        return perceivedRisks.stream()
                .filter(p -> p.perceiverAgentId().equals(agentId) && p.entityId().equals(entityId))
                .findFirst();
    }
}
