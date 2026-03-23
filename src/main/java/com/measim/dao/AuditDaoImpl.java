package com.measim.dao;

import com.measim.model.scoring.AuditEntry;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
public class AuditDaoImpl implements AuditDao {

    private final List<AuditEntry> entries = new ArrayList<>();

    @Override
    public void append(AuditEntry entry) { entries.add(entry); }

    @Override
    public List<AuditEntry> getEntriesForAgent(String agentId) {
        return entries.stream().filter(e -> e.agentId().equals(agentId)).toList();
    }

    @Override
    public List<AuditEntry> getEntriesForTick(int tick) {
        return entries.stream().filter(e -> e.tick() == tick).toList();
    }

    @Override
    public List<AuditEntry> allEntries() { return Collections.unmodifiableList(entries); }

    @Override
    public int size() { return entries.size(); }
}
