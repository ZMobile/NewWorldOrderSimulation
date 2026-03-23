package com.measim.model.scoring;

public record AuditEntry(
        int tick,
        String agentId,
        String axis,
        double previousValue,
        double newValue,
        String formulaVersion,
        String explanation
) {}
