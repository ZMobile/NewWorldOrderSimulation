package com.measim.service.communication;

import com.measim.dao.CommunicationDao;
import com.measim.model.communication.Conversation;
import com.measim.model.communication.Message;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;

@Singleton
public class CommunicationServiceImpl implements CommunicationService {

    private static final String GM_ID = "GAME_MASTER";
    private final CommunicationDao communicationDao;

    @Inject
    public CommunicationServiceImpl(CommunicationDao communicationDao) {
        this.communicationDao = communicationDao;
    }

    @Override
    public void sendAgentMessage(String fromId, String toId, String content,
                                  Message.MessageType type, int tick) {
        communicationDao.log(new Message(tick, fromId, toId,
                Message.Channel.AGENT_TO_AGENT, content, type));
    }

    @Override
    public void broadcast(String senderId, String content, Message.MessageType type, int tick) {
        communicationDao.log(new Message(tick, senderId, "ALL",
                Message.Channel.BROADCAST, content, type));
    }

    @Override
    public void logThought(String entityId, String thought, Message.Channel channel, int tick) {
        communicationDao.log(new Message(tick, entityId, entityId,
                channel, thought, Message.MessageType.THOUGHT));
    }

    @Override
    public Conversation startConversation(String initiatorId, String responderId,
                                           String topic, Message.MessageType initialType,
                                           String initialContent, int tick) {
        String id = "conv_" + UUID.randomUUID().toString().substring(0, 8);
        Conversation conv = new Conversation(id, initiatorId, responderId, topic, tick);

        Message.Channel channel = responderId.equals(GM_ID)
                ? Message.Channel.AGENT_TO_GM
                : Message.Channel.AGENT_TO_AGENT;

        Message initial = new Message(tick, initiatorId, responderId, channel, initialContent, initialType);
        conv.addMessage(initial);
        communicationDao.log(initial);
        communicationDao.startConversation(conv);
        return conv;
    }

    @Override
    public void continueConversation(String conversationId, String senderId,
                                      String content, Message.MessageType type, int tick) {
        communicationDao.getConversation(conversationId).ifPresent(conv -> {
            Message.Channel channel;
            if (senderId.equals(GM_ID)) {
                channel = Message.Channel.GM_TO_AGENT;
            } else if (conv.responderId().equals(GM_ID) || conv.initiatorId().equals(GM_ID)) {
                channel = Message.Channel.AGENT_TO_GM;
            } else {
                channel = Message.Channel.AGENT_TO_AGENT;
            }

            String receiverId = senderId.equals(conv.initiatorId())
                    ? conv.responderId() : conv.initiatorId();
            Message msg = new Message(tick, senderId, receiverId, channel, content, type);
            conv.addMessage(msg);
            communicationDao.log(msg);
        });
    }

    @Override
    public void resolveConversation(String conversationId, Object outcome) {
        communicationDao.getConversation(conversationId).ifPresent(c -> c.resolve(outcome));
    }

    @Override
    public List<Conversation> getActiveConversationsFor(String entityId) {
        return communicationDao.getConversationsForEntity(entityId).stream()
                .filter(c -> c.status() == Conversation.Status.ACTIVE)
                .toList();
    }

    @Override
    public List<Message> getRecentMessages(int limit) {
        return communicationDao.getAllMessages().stream()
                .skip(Math.max(0, communicationDao.getAllMessages().size() - limit))
                .toList();
    }

    @Override
    public List<Message> getThoughtsFor(String entityId, int limit) {
        return communicationDao.getMessagesForEntity(entityId, limit * 3).stream()
                .filter(m -> m.type() == Message.MessageType.THOUGHT
                        || m.type() == Message.MessageType.DECISION_RATIONALE)
                .limit(limit)
                .toList();
    }
}
