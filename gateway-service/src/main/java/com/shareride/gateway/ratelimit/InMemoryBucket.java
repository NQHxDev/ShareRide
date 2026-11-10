package com.shareride.gateway.ratelimit;

/** Thread-safe in-memory token bucket state used for local development and Redis failover. */
public class InMemoryBucket {
    private double tokens;
    private long lastRefreshed;

    public InMemoryBucket(double capacity, long lastRefreshed) {
        this.tokens = capacity;
        this.lastRefreshed = lastRefreshed;
    }

    public synchronized double getTokens() {
        return tokens;
    }

    public synchronized void setTokens(double tokens) {
        this.tokens = tokens;
    }

    public synchronized long getLastRefreshed() {
        return lastRefreshed;
    }

    public synchronized void setLastRefreshed(long lastRefreshed) {
        this.lastRefreshed = lastRefreshed;
    }
}
