package com.measim.dao;

import com.measim.model.externality.ExternalityProfile;
import com.measim.model.externality.PerceivedByproduct;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class ExternalityDaoImpl implements ExternalityDao {

    private final Map<String, ExternalityProfile> profiles = new LinkedHashMap<>();
    private final List<PerceivedByproduct> perceptions = new ArrayList<>();

    @Override public void registerProfile(ExternalityProfile profile) { profiles.put(profile.entityId(), profile); }
    @Override public Optional<ExternalityProfile> getProfile(String entityId) { return Optional.ofNullable(profiles.get(entityId)); }
    @Override public List<ExternalityProfile> getAllProfiles() { return List.copyOf(profiles.values()); }

    @Override public void recordPerceivedByproduct(PerceivedByproduct perceived) {
        perceptions.removeIf(p -> p.perceiverAgentId().equals(perceived.perceiverAgentId())
                && p.entityId().equals(perceived.entityId()));
        perceptions.add(perceived);
    }
    @Override public List<PerceivedByproduct> getPerceptionsForAgent(String agentId) {
        return perceptions.stream().filter(p -> p.perceiverAgentId().equals(agentId)).toList();
    }
    @Override public Optional<PerceivedByproduct> getAgentPerceptionOf(String agentId, String entityId) {
        return perceptions.stream()
                .filter(p -> p.perceiverAgentId().equals(agentId) && p.entityId().equals(entityId))
                .findFirst();
    }
}
