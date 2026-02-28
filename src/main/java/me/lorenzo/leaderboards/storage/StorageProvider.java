package me.lorenzo.leaderboards.storage;

import me.lorenzo.leaderboards.LeaderboardEntry;

import java.util.List;
import java.util.Optional;

/**
 * Storage backend for a leaderboard.
 * Implement this to plug in any persistence or caching layer (e.g. Shelf, MongoDB, Redis).
 *
 * The leaderboard passes the scoreField to getTopSortedBy so implementations
 * can delegate sorting to the underlying store (e.g. SQL ORDER BY, Mongo sort).
 */
public interface StorageProvider {

    void save(LeaderboardEntry entry);

    List<LeaderboardEntry> getTopSortedBy(String scoreField, int limit);

    Optional<LeaderboardEntry> findBy(String field, Object value);

    void removeBy(String field, Object value);

    long size();

    void clear();
}
