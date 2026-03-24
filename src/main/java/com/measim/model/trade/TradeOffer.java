package com.measim.model.trade;

import com.measim.model.economy.ItemType;

import java.util.Map;

/**
 * A trade offer from one agent to another (or to "anyone at this tile").
 * Supports barter (items for items), sale (items for credits), or mixed.
 */
public class TradeOffer {

    public enum Status { PENDING, ACCEPTED, REJECTED, EXPIRED, CANCELLED }

    private final String id;
    private final String offererAgentId;
    private final String targetAgentId;   // null = open offer (anyone at tile/marketplace)
    private final Map<ItemType, Integer> itemsOffered;
    private final Map<ItemType, Integer> itemsRequested;
    private final double creditsOffered;
    private final double creditsRequested;
    private final String message;         // natural language description of the offer
    private final int createdTick;
    private final int expiryTick;         // auto-expire if not accepted
    private Status status;
    private String respondentId;          // who accepted (for open offers)

    public TradeOffer(String id, String offererAgentId, String targetAgentId,
                      Map<ItemType, Integer> itemsOffered, Map<ItemType, Integer> itemsRequested,
                      double creditsOffered, double creditsRequested,
                      String message, int createdTick, int expiryTick) {
        this.id = id;
        this.offererAgentId = offererAgentId;
        this.targetAgentId = targetAgentId;
        this.itemsOffered = Map.copyOf(itemsOffered);
        this.itemsRequested = Map.copyOf(itemsRequested);
        this.creditsOffered = creditsOffered;
        this.creditsRequested = creditsRequested;
        this.message = message;
        this.createdTick = createdTick;
        this.expiryTick = expiryTick;
        this.status = Status.PENDING;
    }

    public void accept(String respondentId) { this.status = Status.ACCEPTED; this.respondentId = respondentId; }
    public void reject() { this.status = Status.REJECTED; }
    public void expire() { this.status = Status.EXPIRED; }
    public void cancel() { this.status = Status.CANCELLED; }

    public boolean isOpen() { return targetAgentId == null; }
    public boolean isPending() { return status == Status.PENDING; }
    public boolean isExpired(int currentTick) { return currentTick >= expiryTick; }

    public String id() { return id; }
    public String offererAgentId() { return offererAgentId; }
    public String targetAgentId() { return targetAgentId; }
    public Map<ItemType, Integer> itemsOffered() { return itemsOffered; }
    public Map<ItemType, Integer> itemsRequested() { return itemsRequested; }
    public double creditsOffered() { return creditsOffered; }
    public double creditsRequested() { return creditsRequested; }
    public String message() { return message; }
    public int createdTick() { return createdTick; }
    public int expiryTick() { return expiryTick; }
    public Status status() { return status; }
    public String respondentId() { return respondentId; }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Offer from ").append(offererAgentId);
        if (targetAgentId != null) sb.append(" to ").append(targetAgentId);
        else sb.append(" (open)");
        sb.append(": giving [");
        if (!itemsOffered.isEmpty()) itemsOffered.forEach((k, v) -> sb.append(k).append("x").append(v).append(" "));
        if (creditsOffered > 0) sb.append(String.format("%.0f credits", creditsOffered));
        sb.append("] for [");
        if (!itemsRequested.isEmpty()) itemsRequested.forEach((k, v) -> sb.append(k).append("x").append(v).append(" "));
        if (creditsRequested > 0) sb.append(String.format("%.0f credits", creditsRequested));
        sb.append("]");
        if (message != null && !message.isEmpty()) sb.append(" - \"").append(message).append("\"");
        return sb.toString();
    }
}
