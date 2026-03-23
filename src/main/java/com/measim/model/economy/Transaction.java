package com.measim.model.economy;

public record Transaction(
        String id,
        String buyerId,
        String sellerId,
        ItemType itemType,
        int quantity,
        double baseValue,
        double netCreditsToSeller,
        double efDelta,
        double ccDelta,
        double rcDelta,
        double ldDiversion,
        int tick
) {
    public double totalDiverted() { return baseValue - netCreditsToSeller; }
}
