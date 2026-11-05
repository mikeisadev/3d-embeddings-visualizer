package com.example.embeddingexplorer.embedding;

import com.example.embeddingexplorer.config.ExplorerProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chooses the embedding client from the configuration and builds the shared rate limiter. */
@Configuration(proxyBeanMethods = false)
class EmbeddingConfig {

    @Bean
    EmbeddingClient embeddingClient(ExplorerProperties properties) {
        return properties.embedding().enabled()
                ? new OpenAiCompatibleEmbeddingClient(properties.embedding())
                : new DisabledEmbeddingClient();
    }

    @Bean
    EmbeddingRateLimiter embeddingRateLimiter(ExplorerProperties properties) {
        int perMinute = properties.searchRateLimitPerMinute();
        return new EmbeddingRateLimiter(new TokenBucket(perMinute, Duration.ofMinutes(1), System::nanoTime), perMinute);
    }
}
