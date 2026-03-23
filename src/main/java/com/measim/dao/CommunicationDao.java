package com.measim.dao;

import com.measim.model.communication.Conversation;
import com.measim.model.communication.Message;

import java.util.List;
import java.util.Optional;

/**
 * Central log of all communication in the simulation.
 * Everything is observable — this is the simulation's "transcript."
 */
public interface CommunicationDao {

    // Messages
    void log(Message message);
    List<Message> getMessagesForEntity(String entityId, int limit);
    List<Message> getMessagesByChannel(Message.Channel channel, int limit);
    List<Message> getMessagesForTick(int tick);
    List<Message> getAllMessages();

    // Conversations
    void startConversation(Conversation conversation);
    Optional<Conversation> getConversation(String id);
    List<Conversation> getActiveConversations();
    List<Conversation> getConversationsForEntity(String entityId);
}
