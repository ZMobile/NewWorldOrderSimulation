package com.measim.model.llm;

import java.util.List;

public record LlmRequest(
        String model,
        String systemPrompt,
        List<Message> messages,
        double temperature,
        int maxTokens,
        RequestPurpose purpose
) {
    public enum RequestPurpose {
        AGENT_DECISION,
        AGENT_COMPLEX_DECISION,
        GAME_MASTER_ADJUDICATION,
        GOVERNANCE_REASONING
    }

    public record Message(String role, String content) {
        public static Message user(String content) { return new Message("user", content); }
        public static Message assistant(String content) { return new Message("assistant", content); }
    }

    public static LlmRequest agentDecision(String model, String systemPrompt, String userPrompt) {
        return new LlmRequest(model, systemPrompt, List.of(Message.user(userPrompt)),
                0.7, 1024, RequestPurpose.AGENT_DECISION);
    }

    public static LlmRequest gameMaster(String model, String systemPrompt, String userPrompt) {
        return new LlmRequest(model, systemPrompt, List.of(Message.user(userPrompt)),
                0.8, 2048, RequestPurpose.GAME_MASTER_ADJUDICATION);
    }
}
