package com.measim.service.scoring;

import com.measim.model.scoring.*;

import java.util.List;

public interface ScoringService {
    ModifierSet computeModifiers(ScoreVector agentScore, SectorBaseline baseline);
    void updateScoreVector(String agentId, ScoreVector score, SectorBaseline baseline,
                           double totalEmissions, double revenue, int humanEmployees,
                           double accumulatedCredits, double commonsScore, int tick);
    void updateBaseline(SectorBaseline baseline, List<SectorBaseline.ActorData> actorData);
    SectorBaseline getSectorBaseline();
}
