package com.measim.model.communication;

/**
 * A message between any two entities in the simulation.
 * ALL messages are logged and observable — this is how we watch the simulation "think."
 */
public record Message(
        int tick,
        String senderId,
        String receiverId,
        Channel channel,
        String content,
        MessageType type
) {
    public enum Channel {
        AGENT_TO_AGENT,       // Direct communication between agents
        AGENT_TO_GM,          // Agent proposing/requesting something from Game Master
        GM_TO_AGENT,          // Game Master responding — setting properties, costs, risks
        GM_INTERNAL,          // Game Master's reasoning (observable thought process)
        AGENT_INTERNAL,       // Agent's reasoning when making decisions (observable thought)
        BROADCAST,            // Public announcement (governance, market signals)
        GM_WORLD_NARRATION    // Game Master narrating world events
    }

    public enum MessageType {
        // Agent-to-agent
        TRADE_PROPOSAL,
        TRADE_RESPONSE,
        INFORMATION_SHARE,
        COALITION_INVITE,
        COALITION_RESPONSE,
        SOCIAL,

        // Agent-to-GM (agent has creative agency — proposes specific solutions)
        INFRASTRUCTURE_PROPOSAL,    // "I want to build X connecting A to B using Y materials"
        RESEARCH_PROPOSAL,          // "I hypothesize that combining X and Y under Z conditions..."
        NOVEL_ACTION_PROPOSAL,      // "I want to attempt X by doing Y"
        COUNTER_PROPOSAL,           // "What if I used Z instead?"

        // GM-to-Agent (GM evaluates — sets properties, costs, risks, outcomes)
        EVALUATION_RESULT,          // "That's feasible. Costs X, properties are Y, risks are Z"
        CLARIFICATION_REQUEST,      // "What materials are you proposing to use?"
        REJECTION,                  // "That violates conservation of energy" / "Tech prerequisites not met"

        // Internal reasoning (observable)
        THOUGHT,                    // Stream of consciousness reasoning
        DECISION_RATIONALE,         // Why a specific decision was made

        // World narration
        EVENT_NARRATION,
        COHERENCE_OBSERVATION
    }
}
