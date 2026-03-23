package com.measim.model.event;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus {

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        List<Consumer<?>> handlers = listeners.get(event.getClass());
        if (handlers != null) {
            for (Consumer<?> handler : handlers) ((Consumer<T>) handler).accept(event);
        }
    }

    public record TickStarted(int tick) {}
    public record TickCompleted(int tick) {}
    public record TransactionCompleted(String transactionId, String buyerId, String sellerId, double value) {}
    public record AgentCreated(String agentId) {}
    public record EnvironmentalCrisis(int tick, int affectedTiles) {}
    public record TechnologyDiscovered(String techId, String discovererAgentId) {}
    public record UbiDistributed(int tick, double perCapita, int recipients) {}
}
