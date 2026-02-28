package me.lorenzo.leaderboards.storage.impl;

import me.lorenzo.leaderboards.LeaderboardEntry;
import me.lorenzo.leaderboards.storage.StorageProvider;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryStorage implements StorageProvider {

    private final CopyOnWriteArrayList<LeaderboardEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void save(LeaderboardEntry entry) {
        entries.add(entry);
    }

    @Override
    public List<LeaderboardEntry> getTopSortedBy(String scoreField, int limit) {
        return entries.stream()
                .sorted(Comparator.comparingDouble(
                        (LeaderboardEntry e) -> toDouble(e.get(scoreField))
                ).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<LeaderboardEntry> findBy(String field, Object value) {
        return entries.stream()
                .filter(e -> Objects.equals(e.get(field), value))
                .findFirst();
    }

    @Override
    public void removeBy(String field, Object value) {
        entries.removeIf(e -> Objects.equals(e.get(field), value));
    }

    @Override
    public long size() {
        return entries.size();
    }

    @Override
    public void clear() {
        entries.clear();
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return 0;
    }
}
