package me.lorenzo.leaderboards;

import me.lorenzo.leaderboards.rest.RestConfig;
import me.lorenzo.leaderboards.rest.RestServer;
import me.lorenzo.services.service.Service;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry for Leaderboard instances, compatible with the Services system.
 * Hosts a single REST server that routes to all registered leaderboards by name.
 *
 * Usage:
 * <pre>
 *   LeaderboardService svc = new LeaderboardService().withRest(8080);
 *   Services.register(svc);
 *
 *   svc.register(Leaderboard.create("kills").scoreField("kills").identityField("player").build());
 *   svc.register(Leaderboard.create("deaths").scoreField("deaths").identityField("player").build());
 *
 *   // GET /kills/top?limit=10
 *   // GET /deaths/rank?player=Steve
 * </pre>
 */
public class LeaderboardService implements Service<LeaderboardService> {

    private final Map<String, Leaderboard> registry = new ConcurrentHashMap<>();
    private RestServer restServer;

    // -------------------------------------------------------------------------
    // REST
    // -------------------------------------------------------------------------

    public LeaderboardService withRest(int port) {
        return withRest(RestConfig.on(port));
    }

    public LeaderboardService withRest(RestConfig config) {
        if (restServer != null) restServer.stop();
        restServer = new RestServer(this, config);
        try {
            restServer.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start REST server on port " + config.port(), e);
        }
        return this;
    }

    // -------------------------------------------------------------------------
    // Registry
    // -------------------------------------------------------------------------

    public LeaderboardService register(Leaderboard leaderboard) {
        registry.put(leaderboard.name(), leaderboard);
        return this;
    }

    public void unregister(String name) {
        registry.remove(name);
    }

    public Optional<Leaderboard> get(String name) {
        return Optional.ofNullable(registry.get(name));
    }

    public Leaderboard getOrThrow(String name) {
        Leaderboard lb = registry.get(name);
        if (lb == null) throw new IllegalArgumentException("No leaderboard registered: " + name);
        return lb;
    }

    public boolean contains(String name) {
        return registry.containsKey(name);
    }

    public Collection<Leaderboard> all() {
        return Collections.unmodifiableCollection(registry.values());
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void stop() {
        if (restServer != null) restServer.stop();
    }

    @Override
    public Class<LeaderboardService> asType() {
        return LeaderboardService.class;
    }
}
