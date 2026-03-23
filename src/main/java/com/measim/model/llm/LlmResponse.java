package com.measim.model.llm;

public record LlmResponse(
        String content,
        int inputTokens,
        int outputTokens,
        double costUsd,
        String model,
        boolean fromCache
) {
    public static LlmResponse empty() {
        return new LlmResponse("", 0, 0, 0, "", false);
    }

    public static LlmResponse cached(String content, String model) {
        return new LlmResponse(content, 0, 0, 0, model, true);
    }
}
