package me.lorenzo.leaderboards.storage.impl;

import me.lorenzo.presence.vault.query.Query;
import me.lorenzo.presence.vault.record.DataRecord;
import me.lorenzo.presence.vault.record.simple.SimpleDataRecord;
import me.lorenzo.leaderboards.LeaderboardEntry;
import me.lorenzo.leaderboards.storage.StorageProvider;
import me.lorenzo.shelf.Shelf;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * StorageProvider backed by Shelf (cache-first) + an optional DataVault for persistence.
 *
 * Usage:
 * <pre>
 *   Shelf shelf = Shelf.create()
 *       .withPersistence(new MongoDataVault(...))
 *       .withCaching(new RedisCaching(...))
 *       .build();
 *
 *   Leaderboard lb = Leaderboard.create("kills")
 *       .scoreField("kills")
 *       .identityField("player")
 *       .withStorage(new ShelfStorage(shelf, "kills"))
 *       .build();
 * </pre>
 *
 * Read path:  Shelf (cache-first → DataVault on miss)
 * Write path: DataVault (if configured) + cache invalidation via Shelf
 * size():     DataVault.count() if vault is present, otherwise in-memory size
 * clear():    DataVault.delete(all) + Shelf.evictAll(collection)
 */
public class ShelfStorage implements StorageProvider {

    private final Shelf shelf;
    private final String collection;

    public ShelfStorage(Shelf shelf, String collection) {
        this.shelf = Objects.requireNonNull(shelf, "shelf");
        this.collection = Objects.requireNonNull(collection, "collection");
    }

    @Override
    public void save(LeaderboardEntry entry) {
        shelf.put(collection, toRecord(entry));
    }

    @Override
    public List<LeaderboardEntry> getTopSortedBy(String scoreField, int limit) {
        // Fetch all (cache-first), sort in memory — DataVault has no ORDER BY support
        return shelf.getAll(collection, Query.all()).stream()
                .sorted(Comparator.comparingDouble(
                        (DataRecord r) -> toDouble(r.get(scoreField))
                ).reversed())
                .limit(limit)
                .map(this::toEntry)
                .toList();
    }

    @Override
    public Optional<LeaderboardEntry> findBy(String field, Object value) {
        return shelf.get(collection, Query.where(field, value))
                .map(this::toEntry);
    }

    @Override
    public void removeBy(String field, Object value) {
        shelf.remove(collection, Query.where(field, value));
    }

    @Override
    public long size() {
        if (shelf.vault() != null) {
            return shelf.vault().count(collection, Query.all());
        }
        return shelf.getAll(collection, Query.all()).size();
    }

    @Override
    public void clear() {
        if (shelf.vault() != null) {
            shelf.vault().delete(collection, Query.all());
        }
        shelf.evictAll(collection);
    }

    private DataRecord toRecord(LeaderboardEntry entry) {
        return new SimpleDataRecord(entry.asMap());
    }

    private LeaderboardEntry toEntry(DataRecord record) {
        LeaderboardEntry.Builder builder = LeaderboardEntry.of();
        record.asMap().forEach(builder::field);
        return builder.build();
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return 0;
    }
}
