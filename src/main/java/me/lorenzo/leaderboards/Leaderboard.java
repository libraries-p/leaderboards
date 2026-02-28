package me.lorenzo.leaderboards;

import me.lorenzo.leaderboards.storage.StorageProvider;
import me.lorenzo.leaderboards.storage.impl.InMemoryStorage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A dynamic, pluggable leaderboard.
 *
 * The data model is fully open — entries are key-value maps (LeaderboardEntry).
 * The implementor decides which field is the score, which is the identity key,
 * and what other fields exist.
 *
 * REST is managed at the LeaderboardService level so that multiple leaderboards
 * can share a single HTTP server. Register leaderboards on a LeaderboardService
 * and call leaderboardService.withRest(port).
 *
 * Usage:
 * <pre>
 *   LeaderboardService svc = new LeaderboardService().withRest(8080);
 *
 *   svc.register(Leaderboard.create("kills")
 *       .scoreField("kills").identityField("player").build());
 *
 *   svc.register(Leaderboard.create("deaths")
 *       .scoreField("deaths").identityField("player").build());
 *
 *   // GET /kills/top?limit=10
 *   // GET /deaths/rank?player=Steve
 * </pre>
 */
public class Leaderboard {

    private final String name;
    private final String scoreField;
    private final String identityField;   // nullable
    private final StorageProvider storage;

    private Leaderboard(Builder builder) {
        this.name = builder.name;
        this.scoreField = builder.scoreField;
        this.identityField = builder.identityField;
        this.storage = builder.storage;
    }

    public static Builder create(String name) {
        return new Builder(name);
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Submits an entry.
     * If identityField is configured and an entry with the same identity value
     * already exists, it is replaced (upsert). Otherwise a new entry is appended.
     */
    public void submit(LeaderboardEntry entry) {
        if (identityField != null) {
            Object idValue = entry.get(identityField);
            if (idValue != null) storage.removeBy(identityField, idValue);
        }
        storage.save(entry);
    }

    /** Convenience overload using a builder lambda. */
    public void submit(Consumer<LeaderboardEntry.Builder> configurator) {
        LeaderboardEntry.Builder builder = LeaderboardEntry.of();
        configurator.accept(builder);
        submit(builder.build());
    }

    /**
     * Adds delta to the score of the entry identified by the configured identityField.
     * If the entry doesn't exist yet, creates it with just the identity + score fields.
     * Requires identityField to be configured.
     *
     * Example: lb.addScore("Steve", 10);
     */
    public void addScore(Object identityValue, double delta) {
        if (identityField == null) throw new IllegalStateException(
                "addScore() requires identityField to be configured on the leaderboard");

        Optional<LeaderboardEntry> existing = storage.findBy(identityField, identityValue);
        LeaderboardEntry.Builder builder = LeaderboardEntry.of();

        if (existing.isPresent()) {
            existing.get().asMap().forEach(builder::field);
        } else {
            builder.field(identityField, identityValue);
        }

        double current = existing.map(e -> toDouble(e.get(scoreField))).orElse(0.0);
        builder.field(scoreField, current + delta);
        submit(builder.build());
    }

    /**
     * Sets the score of an entry to an absolute value, preserving all other fields.
     * Requires identityField to be configured.
     */
    public void setScore(Object identityValue, double score) {
        if (identityField == null) throw new IllegalStateException(
                "setScore() requires identityField to be configured on the leaderboard");

        Optional<LeaderboardEntry> existing = storage.findBy(identityField, identityValue);
        LeaderboardEntry.Builder builder = LeaderboardEntry.of();

        if (existing.isPresent()) {
            existing.get().asMap().forEach(builder::field);
        } else {
            builder.field(identityField, identityValue);
        }

        builder.field(scoreField, score);
        submit(builder.build());
    }

    public void remove(String field, Object value) {
        storage.removeBy(field, value);
    }

    public void clear() {
        storage.clear();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public List<LeaderboardEntry> top(int limit) {
        return storage.getTopSortedBy(scoreField, limit);
    }

    public Optional<LeaderboardEntry> findBy(String field, Object value) {
        return storage.findBy(field, value);
    }

    /** Returns the 1-based rank of the entry matching field=value, or -1 if not found. */
    public long rankOf(String field, Object value) {
        List<LeaderboardEntry> all = storage.getTopSortedBy(scoreField, Integer.MAX_VALUE);
        for (int i = 0; i < all.size(); i++) {
            if (Objects.equals(all.get(i).get(field), value)) return i + 1;
        }
        return -1;
    }

    public long size() {
        return storage.size();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String name()       { return name; }
    public String scoreField() { return scoreField; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder {

        private final String name;
        private String scoreField = "score";
        private String identityField = null;
        private StorageProvider storage = new InMemoryStorage();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "Leaderboard name is required");
        }

        /** The field used as the numeric score for ranking. Default: "score". */
        public Builder scoreField(String field) {
            this.scoreField = Objects.requireNonNull(field);
            return this;
        }

        /**
         * If set, submit() will replace existing entries with the same value for this field.
         * Leave unset to allow multiple entries per identity (append-only).
         */
        public Builder identityField(String field) {
            this.identityField = field;
            return this;
        }

        /** Plugs in a custom storage backend. Default: InMemoryStorage. */
        public Builder withStorage(StorageProvider storage) {
            this.storage = Objects.requireNonNull(storage);
            return this;
        }

        public Leaderboard build() {
            return new Leaderboard(this);
        }
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return 0;
    }
}
