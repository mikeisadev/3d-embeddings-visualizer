package com.example.embeddingexplorer.embedding;

import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import java.time.Duration;
import java.util.Map;

/**
 * Protects the (possibly paid) embedding provider: the RAG search and the live test share one
 * global budget of {@code VIZ_SEARCH_RATE_LIMIT_PER_MINUTE} calls per minute.
 */
public class EmbeddingRateLimiter {

    private final TokenBucket bucket;
    private final int callsPerMinute;

    public EmbeddingRateLimiter(TokenBucket bucket, int callsPerMinute) {
        this.bucket = bucket;
        this.callsPerMinute = callsPerMinute;
    }

    /** Takes one call from the budget, or fails with {@code RATE_LIMITED} and a Retry-After. */
    public void acquire() {
        if (!bucket.tryConsume()) {
            Duration wait = bucket.timeUntilNextToken();
            long seconds = Math.max(1, (long) Math.ceil(wait.toMillis() / 1000.0));
            throw new ExplorerException(
                    ErrorCode.RATE_LIMITED,
                    "At most " + callsPerMinute + " calls per minute to the embedding provider; retry in " + seconds
                            + " s.",
                    Map.of(ExplorerException.RETRY_AFTER_SECONDS, seconds));
        }
    }
}
