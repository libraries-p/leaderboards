package me.lorenzo.leaderboards;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LeaderboardEntry {

    private final Map<String, Object> data;

    private LeaderboardEntry(Map<String, Object> data) {
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public Object get(String field) {
        return data.get(field);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String field, Class<T> type) {
        Object v = data.get(field);
        if (v == null) return null;
        if (type.isInstance(v)) return type.cast(v);
        if (type == Integer.class && v instanceof Number n) return type.cast(n.intValue());
        if (type == Long.class    && v instanceof Number n) return type.cast(n.longValue());
        if (type == Double.class  && v instanceof Number n) return type.cast(n.doubleValue());
        if (type == String.class) return type.cast(v.toString());
        throw new IllegalStateException("Cannot convert '" + field + "' from "
                + v.getClass().getName() + " to " + type.getName());
    }

    public Map<String, Object> asMap() {
        return data;
    }

    public static Builder of() {
        return new Builder();
    }

    public static class Builder {

        private final Map<String, Object> data = new LinkedHashMap<>();

        public Builder field(String name, Object value) {
            data.put(name, value);
            return this;
        }

        public LeaderboardEntry build() {
            return new LeaderboardEntry(data);
        }
    }
}
