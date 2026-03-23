package com.measim.model.agent;

public record MemoryEntry(
        int tick, String type, String description,
        double importanceScore, String relatedAgentId, double creditImpact
) {}
