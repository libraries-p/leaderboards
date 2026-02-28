package me.lorenzo.leaderboards.rest;

public final class RestConfig {

    private final int port;
    private final String basePath;
    private final boolean cors;
    private final int threads;

    private RestConfig(Builder builder) {
        this.port = builder.port;
        String path = builder.basePath;
        this.basePath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        this.cors = builder.cors;
        this.threads = builder.threads;
    }

    /** Minimal config: just a port, no base path, no CORS. */
    public static RestConfig on(int port) {
        return new Builder(port).build();
    }

    public int port()     { return port; }
    public String basePath() { return basePath; }
    public boolean cors() { return cors; }
    public int threads()  { return threads; }

    public static class Builder {

        private final int port;
        private String basePath = "";
        private boolean cors = false;
        private int threads = 4;

        public Builder(int port) {
            this.port = port;
        }

        /** Base path prefixed to all endpoints, e.g. "/kills" → /kills/top, /kills/rank */
        public Builder path(String basePath) {
            this.basePath = basePath;
            return this;
        }

        /** Add Access-Control-Allow-Origin: * to every response. */
        public Builder cors(boolean cors) {
            this.cors = cors;
            return this;
        }

        /** Thread pool size for the HTTP server. Default: 4. */
        public Builder threads(int threads) {
            this.threads = threads;
            return this;
        }

        public RestConfig build() {
            return new RestConfig(this);
        }
    }
}
