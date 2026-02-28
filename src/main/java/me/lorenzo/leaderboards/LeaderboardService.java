package me.lorenzo.leaderboards;

import me.lorenzo.services.service.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional global registry for Leaderboard instances, compatible with the Services system.
 *
 * Usage:
 * <pre>
 *   // Register once at startup
 *   LeaderboardService lbService = new LeaderboardService();
 *   Services.register(lbService);
 *
 *   // Build and register a leaderboard
 *   Leaderboard lb = Leaderboard.create("kills").scoreField("kills").build();
 *   Services.getOrThrow(LeaderboardService.class).register(lb);
 *
 *   // Retrieve from anywhere
 *   Leaderboard lb = Services.getOrThrow(LeaderboardService.class).getOrThrow("kills");
 * </pre>
 */
public class LeaderboardService implements Service<LeaderboardService> {

    private final Map<String, Leaderboard> registry = new ConcurrentHashMap<>();

    public void register(Leaderboard leaderboard) {
        registry.put(leaderboard.name(), leaderboard);
    }

    public void unregister(String name) {
        Leaderboard lb = registry.remove(name);
        if (lb != null) lb.stop();
    }

    public Optional<Leaderboard> get(String name) {
        return Optional.ofNullable(registry.get(name));
    }

    public Leaderboard getOrThrow(String name) {
        Leaderboard lb = registry.get(name);
        if (lb == null) throw new IllegalArgumentException("No leaderboard registered with name: " + name);
        return lb;
    }

    public Collection<Leaderboard> all() {
        return Collections.unmodifiableCollection(registry.values());
    }

    public boolean contains(String name) {
        return registry.containsKey(name);
    }

    @Override
    public Class<LeaderboardService> asType() {
        return LeaderboardService.class;
    }
}
