package com.measim.model.communication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A multi-turn conversation between two entities (agent-agent or agent-GM).
 * Conversations can span multiple ticks. They have a topic, participants,
 * and a thread of messages. This enables back-and-forth clarification
 * instead of single-shot interactions.
 */
public class Conversation {

    public enum Status { ACTIVE, RESOLVED, ABANDONED }

    private final String id;
    private final String initiatorId;
    private final String responderId;
    private final String topic;
    private final int startTick;
    private final List<Message> messages = new ArrayList<>();
    private Status status = Status.ACTIVE;
    private Object outcome;  // The result of the conversation (trade, infrastructure spec, etc.)

    public Conversation(String id, String initiatorId, String responderId, String topic, int startTick) {
        this.id = id;
        this.initiatorId = initiatorId;
        this.responderId = responderId;
        this.topic = topic;
        this.startTick = startTick;
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    public Message lastMessage() {
        return messages.isEmpty() ? null : messages.getLast();
    }

    public boolean needsResponse() {
        if (messages.isEmpty() || status != Status.ACTIVE) return false;
        Message last = lastMessage();
        return last.type() == Message.MessageType.CLARIFICATION_REQUEST
                || last.type() == Message.MessageType.TRADE_PROPOSAL
                || last.type() == Message.MessageType.COALITION_INVITE
                || last.type() == Message.MessageType.INFRASTRUCTURE_PROPOSAL
                || last.type() == Message.MessageType.NOVEL_ACTION_PROPOSAL;
    }

    public int turnCount() { return messages.size(); }
    public boolean isStale(int currentTick, int maxAge) { return currentTick - startTick > maxAge; }

    public void resolve(Object outcome) {
        this.status = Status.RESOLVED;
        this.outcome = outcome;
    }

    public void abandon() { this.status = Status.ABANDONED; }

    public String id() { return id; }
    public String initiatorId() { return initiatorId; }
    public String responderId() { return responderId; }
    public String topic() { return topic; }
    public int startTick() { return startTick; }
    public List<Message> messages() { return Collections.unmodifiableList(messages); }
    public Status status() { return status; }
    public Object outcome() { return outcome; }
}
