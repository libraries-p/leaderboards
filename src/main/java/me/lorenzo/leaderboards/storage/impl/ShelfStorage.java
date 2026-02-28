package me.lorenzo.leaderboards.storage.impl;

import me.lorenzo.presence.vault.DataVault;
import me.lorenzo.presence.vault.query.Query;
import me.lorenzo.presence.vault.record.DataRecord;
import me.lorenzo.presence.vault.record.simple.SimpleDataRecord;
import me.lorenzo.leaderboards.LeaderboardEntry;
import me.lorenzo.leaderboards.storage.StorageProvider;
import me.lorenzo.shelf.Shelf;
import me.lorenzo.shelf.caching.CachingProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * StorageProvider that keeps the full entry list in a CachingProvider
 * and optionally persists to a DataVault.
 *
 * The cache is always the source of truth for reads. On cache miss,
 * the list is loaded from the vault (if configured) and repopulated.
 *
 * Constructors:
 *   new ShelfStorage(cache, "collection")               — cache only, no persistence
 *   new ShelfStorage(vault, cache, "collection")        — persistence + caching
 *   new ShelfStorage(shelf, "collection")               — extracts vault + cache from Shelf
 *
 * Usage:
 * <pre>
 *   // In-memory cache only
 *   new ShelfStorage(new LocalMapCaching(), "kills")
 *
 *   // MongoDB + Redis
 *   new ShelfStorage(mongoVault, new RedisCaching("localhost", 6379), "kills")
 *
 *   // Via Shelf
 *   Shelf shelf = Shelf.create()
 *       .withPersistence(new MongoDataVault(...))
 *       .withCaching(new RedisCaching(...))
 *       .build();
 *   new ShelfStorage(shelf, "kills")
 * </pre>
 */
public class ShelfStorage implements StorageProvider {

    private final DataVault vault;        // nullable — persistence is optional
    private final CachingProvider cache;
    private final String collection;

    /** Cache only — no persistence. */
    public ShelfStorage(CachingProvider cache, String collection) {
        this(null, cache, collection);
    }

    /** Persistence + caching. */
    public ShelfStorage(DataVault vault, CachingProvider cache, String collection) {
        this.vault = vault;
        this.cache = Objects.requireNonNull(cache, "cache");
        this.collection = Objects.requireNonNull(collection, "collection");
    }

    /** Convenience: extracts vault and cache from an existing Shelf. */
    public ShelfStorage(Shelf shelf, String collection) {
        this(shelf.vault(), shelf.cache(), collection);
    }

    // -------------------------------------------------------------------------

    @Override
    public void save(LeaderboardEntry entry) {
        if (vault != null) vault.insert(collection, toRecord(entry));

        List<DataRecord> list = cachedList();
        list.add(toRecord(entry));
        cache.putList(collection, list);
    }

    @Override
    public List<LeaderboardEntry> getTopSortedBy(String scoreField, int limit) {
        return cachedList().stream()
                .sorted(Comparator.comparingDouble(
                        (DataRecord r) -> toDouble(r.get(scoreField))
                ).reversed())
                .limit(limit)
                .map(this::toEntry)
                .toList();
    }

    @Override
    public Optional<LeaderboardEntry> findBy(String field, Object value) {
        return cachedList().stream()
                .filter(r -> Objects.equals(r.get(field), value))
                .findFirst()
                .map(this::toEntry);
    }

    @Override
    public void removeBy(String field, Object value) {
        if (vault != null) vault.delete(collection, Query.where(field, value));

        List<DataRecord> list = cachedList();
        list.removeIf(r -> Objects.equals(r.get(field), value));
        cache.putList(collection, list);
    }

    @Override
    public long size() {
        return cachedList().size();
    }

    @Override
    public void clear() {
        if (vault != null) vault.delete(collection, Query.all());
        cache.putList(collection, List.of());
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Returns a mutable copy of the cached list.
     * On cache miss, loads from vault if available.
     */
    private List<DataRecord> cachedList() {
        Optional<List<DataRecord>> cached = cache.getList(collection);
        if (cached.isPresent()) return new ArrayList<>(cached.get());

        if (vault != null) {
            List<DataRecord> all = vault.find(collection, Query.all());
            cache.putList(collection, all);
            return new ArrayList<>(all);
        }

        return new ArrayList<>();
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
