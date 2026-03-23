package com.measim.model.economy;

public record Order(
        String agentId,
        ItemType itemType,
        int quantity,
        double pricePerUnit,
        OrderSide side,
        int tickPlaced
) {
    public enum OrderSide { BUY, SELL }
    public double totalValue() { return quantity * pricePerUnit; }
}
