package com.measim.dao;

import com.measim.model.scoring.AuditEntry;

import java.util.List;

public interface AuditDao {
    void append(AuditEntry entry);
    List<AuditEntry> getEntriesForAgent(String agentId);
    List<AuditEntry> getEntriesForTick(int tick);
    List<AuditEntry> allEntries();
    int size();
}
