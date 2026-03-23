package com.measim.model.governance;

public record DisputeCase(
        String id,
        String filingAgentId,
        String governmentId,
        DisputeType type,
        String description,
        int tickFiled,
        DisputeStatus status,
        String resolution
) {
    public enum DisputeType { MEASUREMENT, FORMULA, WRONGFUL_SCORING }
    public enum DisputeStatus { FILED, UNDER_REVIEW, RESOLVED, DISMISSED }

    public DisputeCase withStatus(DisputeStatus newStatus, String resolution) {
        return new DisputeCase(id, filingAgentId, governmentId, type, description, tickFiled, newStatus, resolution);
    }
}
