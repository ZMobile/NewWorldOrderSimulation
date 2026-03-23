package com.measim.model.agent;

import java.util.*;
import java.util.stream.Collectors;

public class MemoryStream {

    private final int maxSize;
    private final LinkedList<MemoryEntry> entries = new LinkedList<>();

    public MemoryStream(int maxSize) { this.maxSize = maxSize; }

    public void add(MemoryEntry entry) {
        entries.addLast(entry);
        while (entries.size() > maxSize) {
            entries.stream().min(Comparator.comparingDouble(MemoryEntry::importanceScore))
                    .ifPresent(entries::remove);
        }
    }

    public List<MemoryEntry> getRecent(int count) {
        int start = Math.max(0, entries.size() - count);
        return new ArrayList<>(entries.subList(start, entries.size()));
    }

    public List<MemoryEntry> getMostImportant(int count) {
        return entries.stream().sorted(Comparator.comparingDouble(MemoryEntry::importanceScore).reversed())
                .limit(count).collect(Collectors.toList());
    }

    public List<MemoryEntry> getRelevant(String keyword, int count) {
        return entries.stream()
                .filter(e -> e.description().toLowerCase().contains(keyword.toLowerCase()))
                .sorted(Comparator.comparingDouble(MemoryEntry::importanceScore).reversed())
                .limit(count).collect(Collectors.toList());
    }

    public List<MemoryEntry> getByType(String type, int count) {
        return entries.stream().filter(e -> e.type().equals(type))
                .sorted(Comparator.comparingInt(MemoryEntry::tick).reversed())
                .limit(count).collect(Collectors.toList());
    }

    public String buildContextSummary(int maxEntries) {
        List<MemoryEntry> recent = getRecent(maxEntries / 2);
        List<MemoryEntry> important = getMostImportant(maxEntries / 2);
        Set<MemoryEntry> combined = new LinkedHashSet<>();
        combined.addAll(important);
        combined.addAll(recent);
        StringBuilder sb = new StringBuilder();
        sb.append("Memory summary (").append(combined.size()).append(" entries):\n");
        for (MemoryEntry entry : combined) {
            sb.append(String.format("  [tick %d, %s] %s (importance: %.1f)\n",
                    entry.tick(), entry.type(), entry.description(), entry.importanceScore()));
        }
        return sb.toString();
    }

    public int size() { return entries.size(); }
}
