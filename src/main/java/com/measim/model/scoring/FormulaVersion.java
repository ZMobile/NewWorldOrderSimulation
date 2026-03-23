package com.measim.model.scoring;

public record FormulaVersion(String version, int effectiveTick) {
    public static final FormulaVersion INITIAL = new FormulaVersion("v0.1.0", 0);
}
