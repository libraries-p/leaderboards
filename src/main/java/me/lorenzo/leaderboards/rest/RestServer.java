package me.lorenzo.leaderboards.rest;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.lorenzo.leaderboards.Leaderboard;
import me.lorenzo.leaderboards.LeaderboardEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Lightweight embedded HTTP server exposing GET-only endpoints for a Leaderboard.
 *
 * Endpoints (relative to basePath):
 *   GET /top?limit=10          → JSON array of top N entries
 *   GET /entry?{field}={value} → JSON object of a single entry
 *   GET /rank?{field}={value}  → {"rank": N}
 *   GET /size                  → {"size": N}
 */
public class RestServer {

    private final Leaderboard leaderboard;
    private final RestConfig config;
    private final Gson gson = new Gson();

    private HttpServer server;
    private ExecutorService executor;

    public RestServer(Leaderboard leaderboard, RestConfig config) {
        this.leaderboard = leaderboard;
        this.config = config;
    }

    public void start() throws IOException {
        executor = Executors.newFixedThreadPool(config.threads());
        server = HttpServer.create(new InetSocketAddress(config.port()), 0);

        String base = config.basePath();
        server.createContext(base + "/top",   this::handleTop);
        server.createContext(base + "/entry", this::handleEntry);
        server.createContext(base + "/rank",  this::handleRank);
        server.createContext(base + "/size",  this::handleSize);

        server.setExecutor(executor);
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdown();
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    // GET /top?limit=10
    private void handleTop(HttpExchange ex) throws IOException {
        if (!isGet(ex)) { respond(ex, 405, ""); return; }

        Map<String, String> params = queryParams(ex);
        int limit = Optional.ofNullable(params.get("limit"))
                .map(s -> {
                    try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 10; }
                })
                .filter(n -> n > 0)
                .orElse(10);

        List<Map<String, Object>> top = leaderboard.top(limit).stream()
                .map(LeaderboardEntry::asMap)
                .toList();

        respond(ex, 200, gson.toJson(top));
    }

    // GET /entry?field=value
    private void handleEntry(HttpExchange ex) throws IOException {
        if (!isGet(ex)) { respond(ex, 405, ""); return; }

        Map<String, String> params = queryParams(ex);
        if (params.isEmpty()) { respond(ex, 400, error("Missing query parameter")); return; }

        Map.Entry<String, String> param = params.entrySet().iterator().next();
        Optional<LeaderboardEntry> entry = leaderboard.findBy(param.getKey(), param.getValue());

        if (entry.isEmpty()) { respond(ex, 404, error("Entry not found")); return; }
        respond(ex, 200, gson.toJson(entry.get().asMap()));
    }

    // GET /rank?field=value
    private void handleRank(HttpExchange ex) throws IOException {
        if (!isGet(ex)) { respond(ex, 405, ""); return; }

        Map<String, String> params = queryParams(ex);
        if (params.isEmpty()) { respond(ex, 400, error("Missing query parameter")); return; }

        Map.Entry<String, String> param = params.entrySet().iterator().next();
        long rank = leaderboard.rankOf(param.getKey(), param.getValue());

        if (rank == -1) { respond(ex, 404, error("Entry not found")); return; }
        respond(ex, 200, gson.toJson(Map.of("rank", rank)));
    }

    // GET /size
    private void handleSize(HttpExchange ex) throws IOException {
        if (!isGet(ex)) { respond(ex, 405, ""); return; }
        respond(ex, 200, gson.toJson(Map.of("size", leaderboard.size())));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isGet(HttpExchange ex) {
        return "GET".equalsIgnoreCase(ex.getRequestMethod());
    }

    private Map<String, String> queryParams(HttpExchange ex) {
        String query = ex.getRequestURI().getQuery();
        if (query == null || query.isBlank()) return Map.of();
        return Arrays.stream(query.split("&"))
                .map(p -> p.split("=", 2))
                .filter(p -> p.length == 2)
                .collect(Collectors.toMap(
                        p -> URLDecoder.decode(p[0], StandardCharsets.UTF_8),
                        p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8),
                        (a, b) -> a
                ));
    }

    private void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (config.cors()) ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private String error(String message) {
        return gson.toJson(Map.of("error", message));
    }
}
