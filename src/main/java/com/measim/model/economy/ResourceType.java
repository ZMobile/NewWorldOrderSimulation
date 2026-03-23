package com.measim.model.economy;

public enum   ResourceType {
    MINERAL,
    ENERGY,
    FOOD_LAND,
    WATER_RESOURCE,
    TIMBER;

    public String displayName() {
        return name().charAt(0) + name().substring(1).toLowerCase().replace('_', ' ');
    }
}
