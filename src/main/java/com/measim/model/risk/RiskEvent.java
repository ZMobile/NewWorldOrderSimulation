package com.measim.model.risk;

import java.util.Map;

/**
 * A risk that has been triggered. The deterministic engine fires these;
 * the GM adjudicates the specific consequences.
 */
public record RiskEvent(
        String id,
        String riskId,
        String entityId,
        RiskProfile.EntityType entityType,
        String riskName,
        double severity,
        int tick,
        Map<String, Object> consequences,
        String gmNarrative
) {}
