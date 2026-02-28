package me.lorenzo.leaderboards.rest;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.lorenzo.leaderboards.Leaderboard;
import me.lorenzo.leaderboards.LeaderboardEntry;
import me.lorenzo.leaderboards.LeaderboardService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Single embedded HTTP server that routes GET requests to all leaderboards
 * registered in a LeaderboardService.
 *
 * URL pattern: /{leaderboardName}/{action}
 *   GET /kills/top?limit=10
 *   GET /kills/entry?player=Steve
 *   GET /kills/rank?player=Steve
 *   GET /kills/size
 *
 * With basePath "/lb":
 *   GET /lb/kills/top
 *   GET /lb/deaths/rank?player=Steve
 */
public class RestServer {

    private final LeaderboardService leaderboardService;
    private final RestConfig config;
    private final Gson gson = new Gson();

    private HttpServer server;
    private ExecutorService executor;

    public RestServer(LeaderboardService leaderboardService, RestConfig config) {
        this.leaderboardService = leaderboardService;
        this.config = config;
    }

    public void start() throws IOException {
        executor = Executors.newFixedThreadPool(config.threads());
        server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        String ctx = config.basePath().isEmpty() ? "/" : config.basePath();
        server.createContext(ctx, this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdown();
    }

    // -------------------------------------------------------------------------
    // Routing
    // -------------------------------------------------------------------------

    private void handle(HttpExchange ex) throws IOException {
        if (!isGet(ex)) { respond(ex, 405, ""); return; }

        // Strip basePath prefix and split: /{name}/{action}
        String path = ex.getRequestURI().getPath();
        String base = config.basePath();
        if (!base.isEmpty() && path.startsWith(base)) {
            path = path.substring(base.length());
        }

        String[] parts = path.split("/");
        // parts[0] = "" (leading slash), parts[1] = name, parts[2] = action
        if (parts.length < 3 || parts[1].isBlank()) {
            respond(ex, 400, error("Usage: /" + (base.isEmpty() ? "" : base + "/") + "{leaderboard}/{action}"));
            return;
        }

        String lbName = parts[1];
        String action = parts[2];

        Optional<Leaderboard> lb = leaderboardService.get(lbName);
        if (lb.isEmpty()) {
            respond(ex, 404, error("Leaderboard not found: " + lbName));
            return;
        }

        switch (action) {
            case "top"   -> handleTop(ex, lb.get());
            case "entry" -> handleEntry(ex, lb.get());
            case "rank"  -> handleRank(ex, lb.get());
            case "size"  -> respond(ex, 200, gson.toJson(Map.of("size", lb.get().size())));
            default      -> respond(ex, 404, error("Unknown action: " + action));
        }
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    // GET /{name}/top?limit=10
    private void handleTop(HttpExchange ex, Leaderboard lb) throws IOException {
        Map<String, String> params = queryParams(ex);
        int limit = Optional.ofNullable(params.get("limit"))
                .map(s -> { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 10; } })
                .filter(n -> n > 0)
                .orElse(10);

        List<Map<String, Object>> top = lb.top(limit).stream()
                .map(LeaderboardEntry::asMap)
                .toList();

        respond(ex, 200, gson.toJson(top));
    }

    // GET /{name}/entry?field=value
    private void handleEntry(HttpExchange ex, Leaderboard lb) throws IOException {
        Map<String, String> params = queryParams(ex);
        if (params.isEmpty()) { respond(ex, 400, error("Missing query parameter")); return; }

        Map.Entry<String, String> param = params.entrySet().iterator().next();
        Optional<LeaderboardEntry> entry = lb.findBy(param.getKey(), param.getValue());

        if (entry.isEmpty()) { respond(ex, 404, error("Entry not found")); return; }
        respond(ex, 200, gson.toJson(entry.get().asMap()));
    }

    // GET /{name}/rank?field=value
    private void handleRank(HttpExchange ex, Leaderboard lb) throws IOException {
        Map<String, String> params = queryParams(ex);
        if (params.isEmpty()) { respond(ex, 400, error("Missing query parameter")); return; }

        Map.Entry<String, String> param = params.entrySet().iterator().next();
        long rank = lb.rankOf(param.getKey(), param.getValue());

        if (rank == -1) { respond(ex, 404, error("Entry not found")); return; }
        respond(ex, 200, gson.toJson(Map.of("rank", rank)));
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
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
    }

    private String error(String message) {
        return gson.toJson(Map.of("error", message));
    }
}
