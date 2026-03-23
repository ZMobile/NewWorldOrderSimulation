package com.measim.model.gamemaster;

public enum DiscoveryCategory {
    PRODUCTION_CHAIN_IMPROVEMENT(1, "Optimizations to existing chains"),
    NEW_PRODUCTION_CHAIN(2, "New ways to combine resources"),
    NEW_RESOURCE(3, "New extractable resource types"),
    INFRASTRUCTURE_TECH(4, "Changes the rules of the world");

    private final int level;
    private final String description;

    DiscoveryCategory(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int level() { return level; }
    public String description() { return description; }
}
