package com.example.embeddingexplorer.embedding;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * A classic token bucket: up to {@code capacity} calls at once, refilled continuously at
 * {@code capacity} tokens per {@code period}. In memory and global, which is enough for one
 * backend instance in a classroom.
 */
public final class TokenBucket {

    private final double capacity;
    private final double tokensPerNano;
    private final LongSupplier nanoClock;
    private double tokens;
    private long lastRefill;

    public TokenBucket(int capacity, Duration period, LongSupplier nanoClock) {
        if (capacity <= 0 || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("Capacity and period must be positive");
        }
        this.capacity = capacity;
        this.tokensPerNano = capacity / (double) period.toNanos();
        this.nanoClock = nanoClock;
        this.tokens = capacity;
        this.lastRefill = nanoClock.getAsLong();
    }

    /** Takes one token when available. */
    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    /** How long until the next token is available (zero when one is available now). */
    public synchronized Duration timeUntilNextToken() {
        refill();
        if (tokens >= 1) {
            return Duration.ZERO;
        }
        return Duration.ofNanos((long) Math.ceil((1 - tokens) / tokensPerNano));
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        tokens = Math.min(capacity, tokens + (now - lastRefill) * tokensPerNano);
        lastRefill = now;
    }
}
