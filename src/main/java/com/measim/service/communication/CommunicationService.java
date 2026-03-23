package com.measim.service.communication;

import com.measim.model.communication.Conversation;
import com.measim.model.communication.Message;

import java.util.List;

public interface CommunicationService {

    // Agent-to-agent
    void sendAgentMessage(String fromId, String toId, String content,
                           Message.MessageType type, int tick);

    // Broadcast
    void broadcast(String senderId, String content, Message.MessageType type, int tick);

    // Observable internal reasoning
    void logThought(String entityId, String thought, Message.Channel channel, int tick);

    // Multi-turn conversations (agent-GM or agent-agent)
    Conversation startConversation(String initiatorId, String responderId,
                                    String topic, Message.MessageType initialType,
                                    String initialContent, int tick);
    void continueConversation(String conversationId, String senderId,
                               String content, Message.MessageType type, int tick);
    void resolveConversation(String conversationId, Object outcome);

    // Queries
    List<Conversation> getActiveConversationsFor(String entityId);
    List<Message> getRecentMessages(int limit);
    List<Message> getThoughtsFor(String entityId, int limit);
}
