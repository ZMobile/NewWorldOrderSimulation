package com.measim.model.agent;

public class Agent {

    private final IdentityProfile identity;
    private final AgentState state;
    private final MemoryStream memory;

    public Agent(IdentityProfile identity, AgentState state, int memoryCapacity) {
        this.identity = identity;
        this.state = state;
        this.memory = new MemoryStream(memoryCapacity);
    }

    public void addMemory(MemoryEntry entry) { memory.add(entry); }

    public IdentityProfile identity() { return identity; }
    public AgentState state() { return state; }
    public MemoryStream memory() { return memory; }
    public String id() { return identity.id(); }
    public String name() { return identity.name(); }
}
