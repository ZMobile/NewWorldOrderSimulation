package com.measim.model.economy;

public enum ProductType {
    BASIC_GOODS, CONSTRUCTION, TECHNOLOGY, FOOD, LUXURY, MEDICINE;

    public String displayName() {
        return name().charAt(0) + name().substring(1).toLowerCase().replace('_', ' ');
    }
}
