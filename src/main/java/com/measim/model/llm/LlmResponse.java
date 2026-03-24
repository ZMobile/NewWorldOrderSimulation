package com.measim.model.llm;

import java.util.List;
import java.util.Map;

public record LlmResponse(
        String content,
        int inputTokens,
        int outputTokens,
        double costUsd,
        String model,
        boolean fromCache,
        String stopReason,
        List<ToolUseBlock> toolUseBlocks
) {
    /** A tool_use block from the API response. */
    public record ToolUseBlock(String id, String name, Map<String, Object> input) {}

    public boolean hasToolUse() {
        return "tool_use".equals(stopReason) && toolUseBlocks != null && !toolUseBlocks.isEmpty();
    }

    /** Convert tool_use blocks to LlmRequest ContentBlocks for the conversation chain. */
    public List<LlmRequest.ContentBlock> toAssistantContentBlocks() {
        var blocks = new java.util.ArrayList<LlmRequest.ContentBlock>();
        if (content != null && !content.isEmpty()) {
            blocks.add(LlmRequest.ContentBlock.text(content));
        }
        if (toolUseBlocks != null) {
            for (var tu : toolUseBlocks) {
                blocks.add(LlmRequest.ContentBlock.toolUse(tu.id(), tu.name(), tu.input()));
            }
        }
        return blocks;
    }

    // ========== Legacy constructors for backwards compatibility ==========

    public LlmResponse(String content, int inputTokens, int outputTokens,
                        double costUsd, String model, boolean fromCache) {
        this(content, inputTokens, outputTokens, costUsd, model, fromCache, "end_turn", List.of());
    }

    public static LlmResponse empty() {
        return new LlmResponse("", 0, 0, 0, "", false, "end_turn", List.of());
    }

    public static LlmResponse cached(String content, String model) {
        return new LlmResponse(content, 0, 0, 0, model, true, "end_turn", List.of());
    }
}
