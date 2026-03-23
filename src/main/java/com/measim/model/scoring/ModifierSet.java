package com.measim.model.scoring;

public record ModifierSet(
        double environmentalFootprint,
        double commonsContribution,
        double resourceConcentration,
        double laborDisplacementRate
) {
    public static final ModifierSet NEUTRAL = new ModifierSet(1.0, 1.0, 1.0, 0.0);

    public double combinedMultiplier() {
        return environmentalFootprint * commonsContribution * resourceConcentration * (1.0 - laborDisplacementRate);
    }
}
