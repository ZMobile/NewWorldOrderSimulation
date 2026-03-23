package com.measim.model.economy;

public record ItemType(String id, Category category) {

    public enum Category { RESOURCE, PRODUCT, CUSTOM }

    public static ItemType of(ResourceType r) { return new ItemType(r.name(), Category.RESOURCE); }
    public static ItemType of(ProductType p) { return new ItemType(p.name(), Category.PRODUCT); }
    public static ItemType custom(String id) { return new ItemType(id, Category.CUSTOM); }
}
