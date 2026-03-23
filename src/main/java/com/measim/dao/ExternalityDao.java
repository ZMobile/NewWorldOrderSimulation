package com.measim.dao;

import com.measim.model.externality.ExternalityProfile;
import com.measim.model.externality.PerceivedByproduct;

import java.util.List;
import java.util.Optional;

public interface ExternalityDao {
    void registerProfile(ExternalityProfile profile);
    Optional<ExternalityProfile> getProfile(String entityId);
    List<ExternalityProfile> getAllProfiles();

    void recordPerceivedByproduct(PerceivedByproduct perceived);
    List<PerceivedByproduct> getPerceptionsForAgent(String agentId);
    Optional<PerceivedByproduct> getAgentPerceptionOf(String agentId, String entityId);
}
