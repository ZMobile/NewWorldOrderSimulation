package com.measim.dao;

import com.measim.model.llm.LlmRequest;
import com.measim.model.llm.LlmResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Data access layer for LLM API communication.
 * Handles HTTP calls, response caching, and cost tracking.
 */
public interface LlmDao {
    CompletableFuture<LlmResponse> sendRequest(LlmRequest request);
    CompletableFuture<List<LlmResponse>> sendBatch(List<LlmRequest> requests);
    double totalSpent();
    double budgetRemaining();
    int totalCalls();
    boolean canAfford(LlmRequest request);
    boolean isAvailable();
}
