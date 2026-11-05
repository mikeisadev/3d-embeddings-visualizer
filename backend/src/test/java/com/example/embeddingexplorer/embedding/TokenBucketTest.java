package com.example.embeddingexplorer.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    private final AtomicLong now = new AtomicLong();
    private final TokenBucket bucket = new TokenBucket(3, Duration.ofMinutes(1), now::get);

    @Test
    void allowsABurstUpToTheCapacity() {
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void refillsContinuously() {
        for (int i = 0; i < 3; i++) {
            bucket.tryConsume();
        }
        assertThat(bucket.timeUntilNextToken()).isEqualTo(Duration.ofSeconds(20));

        now.addAndGet(Duration.ofSeconds(19).toNanos());
        assertThat(bucket.tryConsume()).isFalse();
        now.addAndGet(Duration.ofSeconds(1).toNanos());
        assertThat(bucket.tryConsume()).isTrue();
    }

    @Test
    void neverStoresMoreThanTheCapacity() {
        now.addAndGet(Duration.ofHours(1).toNanos());
        for (int i = 0; i < 3; i++) {
            assertThat(bucket.tryConsume()).isTrue();
        }
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void theRateLimiterFailsWithRetryAfter() {
        EmbeddingRateLimiter limiter = new EmbeddingRateLimiter(bucket, 3);
        for (int i = 0; i < 3; i++) {
            limiter.acquire();
        }

        assertThatThrownBy(limiter::acquire).isInstanceOfSatisfying(ExplorerException.class, error -> {
            assertThat(error.code()).isEqualTo(ErrorCode.RATE_LIMITED);
            assertThat(error.properties()).containsEntry(ExplorerException.RETRY_AFTER_SECONDS, 20L);
        });
    }

    @Test
    void rejectsAnInvalidConfiguration() {
        assertThatThrownBy(() -> new TokenBucket(0, Duration.ofMinutes(1), now::get))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
