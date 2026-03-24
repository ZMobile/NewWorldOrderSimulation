package com.measim.model.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record LlmRequest(
        String model,
        String systemPrompt,
        List<Message> messages,
        double temperature,
        int maxTokens,
        RequestPurpose purpose,
        List<ToolDefinition> tools
) {
    public enum RequestPurpose {
        AGENT_DECISION,
        AGENT_COMPLEX_DECISION,
        GAME_MASTER_ADJUDICATION,
        GOVERNANCE_REASONING
    }

    /**
     * A message in the conversation. Content can be a simple string or structured content blocks.
     */
    public record Message(String role, List<ContentBlock> contentBlocks) {
        /** Simple text message (backwards-compatible). */
        public static Message user(String content) {
            return new Message("user", List.of(ContentBlock.text(content)));
        }
        public static Message assistant(String content) {
            return new Message("assistant", List.of(ContentBlock.text(content)));
        }
        /** Assistant message containing tool_use blocks (from API response). */
        public static Message assistantToolUse(List<ContentBlock> blocks) {
            return new Message("assistant", blocks);
        }
        /** User message containing tool_result blocks (our response to tool calls). */
        public static Message toolResults(List<ContentBlock> results) {
            return new Message("user", results);
        }

        /** Get simple text content (first text block). */
        public String content() {
            return contentBlocks.stream()
                    .filter(b -> b.type() == ContentBlock.Type.TEXT)
                    .map(ContentBlock::text)
                    .findFirst().orElse("");
        }
    }

    /**
     * Content block in a message — can be text, tool_use, or tool_result.
     */
    public record ContentBlock(
            Type type,
            String text,           // for TEXT
            String toolUseId,      // for TOOL_USE and TOOL_RESULT
            String toolName,       // for TOOL_USE
            Map<String, Object> toolInput, // for TOOL_USE
            String toolResult      // for TOOL_RESULT
    ) {
        public enum Type { TEXT, TOOL_USE, TOOL_RESULT }

        public static ContentBlock text(String text) {
            return new ContentBlock(Type.TEXT, text, null, null, null, null);
        }
        public static ContentBlock toolUse(String id, String name, Map<String, Object> input) {
            return new ContentBlock(Type.TOOL_USE, null, id, name, input, null);
        }
        public static ContentBlock toolResult(String toolUseId, String result) {
            return new ContentBlock(Type.TOOL_RESULT, null, toolUseId, null, null, result);
        }
    }

    /**
     * Tool definition for the Anthropic API tools parameter.
     */
    public record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {}

    // ========== Factory methods ==========

    public static LlmRequest agentDecision(String model, String systemPrompt, String userPrompt) {
        return new LlmRequest(model, systemPrompt, List.of(Message.user(userPrompt)),
                0.7, 1024, RequestPurpose.AGENT_DECISION, List.of());
    }

    public static LlmRequest gameMaster(String model, String systemPrompt, String userPrompt) {
        return new LlmRequest(model, systemPrompt, List.of(Message.user(userPrompt)),
                0.8, 2048, RequestPurpose.GAME_MASTER_ADJUDICATION, List.of());
    }

    public static LlmRequest gameMasterWithTools(String model, String systemPrompt, String userPrompt,
                                                   List<ToolDefinition> tools) {
        return new LlmRequest(model, systemPrompt, List.of(Message.user(userPrompt)),
                0.8, 4096, RequestPurpose.GAME_MASTER_ADJUDICATION, tools);
    }

    /** Create a follow-up request continuing a tool conversation. */
    public LlmRequest withMessages(List<Message> newMessages) {
        return new LlmRequest(model, systemPrompt, newMessages, temperature, maxTokens, purpose, tools);
    }

    public boolean hasTools() { return tools != null && !tools.isEmpty(); }
}
