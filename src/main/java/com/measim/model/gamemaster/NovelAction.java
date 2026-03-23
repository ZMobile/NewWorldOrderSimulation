package com.measim.model.gamemaster;

/**
 * An agent action that falls outside the deterministic rules and needs GM adjudication.
 * Any archetype can trigger these — this is how agents interact with the GM beyond research.
 */
public record NovelAction(
        String agentId,
        String archetypeName,
        NovelActionType type,
        String description,
        double creditStake,
        int tickProposed
) {
    public enum NovelActionType {
        // Entrepreneur: "I want to open a new kind of business"
        NOVEL_BUSINESS,

        // Exploiter: "I found a loophole in the scoring system"
        SYSTEM_GAMING,

        // Cooperator/Philanthropist: "I want to fund a community project"
        PUBLIC_WORKS,

        // Politician: "I want to form a coalition to change the formula"
        POLITICAL_CAMPAIGN,

        // Artisan: "I want to create a unique product the market hasn't seen"
        ARTISANAL_CREATION,

        // Automator: "I want to build a novel automation setup"
        NOVEL_AUTOMATION,

        // Accumulator: "I want to create a financial instrument"
        FINANCIAL_INNOVATION,

        // Optimizer: "I want to restructure my operations in a novel way"
        OPERATIONAL_RESTRUCTURE,

        // Free Rider: "I want to find a way to get more from UBI"
        UBI_EXPLOITATION,

        // Regulator: "I want to propose a new type of compliance framework"
        REGULATORY_INNOVATION,

        // Innovator: "I have a wild theory about combining resources"
        SPECULATIVE_RESEARCH,

        // Any agent in crisis
        DESPERATE_MEASURE
    }
}
