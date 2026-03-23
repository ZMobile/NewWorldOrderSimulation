package com.measim.model.gamemaster;

public record ResearchProposal(
        String agentId,
        String direction,
        double creditInvestment,
        int tickSubmitted,
        int requiredTicks,
        String hypothesis
) {
    public boolean isReady(int currentTick) {
        return currentTick >= tickSubmitted + requiredTicks;
    }
}
