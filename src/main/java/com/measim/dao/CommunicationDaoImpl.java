package com.measim.dao;

import com.measim.model.communication.Conversation;
import com.measim.model.communication.Message;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class CommunicationDaoImpl implements CommunicationDao {

    private final List<Message> messages = new ArrayList<>();
    private final Map<String, Conversation> conversations = new LinkedHashMap<>();

    @Override
    public void log(Message message) { messages.add(message); }

    @Override
    public List<Message> getMessagesForEntity(String entityId, int limit) {
        return messages.stream()
                .filter(m -> entityId.equals(m.senderId()) || entityId.equals(m.receiverId()))
                .skip(Math.max(0, messages.size() - limit))
                .toList();
    }

    @Override
    public List<Message> getMessagesByChannel(Message.Channel channel, int limit) {
        return messages.stream()
                .filter(m -> m.channel() == channel)
                .skip(Math.max(0, messages.size() - limit))
                .toList();
    }

    @Override
    public List<Message> getMessagesForTick(int tick) {
        return messages.stream().filter(m -> m.tick() == tick).toList();
    }

    @Override
    public List<Message> getAllMessages() { return Collections.unmodifiableList(messages); }

    @Override
    public void startConversation(Conversation conversation) {
        conversations.put(conversation.id(), conversation);
    }

    @Override
    public Optional<Conversation> getConversation(String id) {
        return Optional.ofNullable(conversations.get(id));
    }

    @Override
    public List<Conversation> getActiveConversations() {
        return conversations.values().stream()
                .filter(c -> c.status() == Conversation.Status.ACTIVE)
                .toList();
    }

    @Override
    public List<Conversation> getConversationsForEntity(String entityId) {
        return conversations.values().stream()
                .filter(c -> entityId.equals(c.initiatorId()) || entityId.equals(c.responderId()))
                .toList();
    }
}
