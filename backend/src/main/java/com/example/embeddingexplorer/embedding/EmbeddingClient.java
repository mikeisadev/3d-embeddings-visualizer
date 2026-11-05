package com.example.embeddingexplorer.embedding;

import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;

/**
 * Turns a text into an embedding with the provider configured for the RAG simulation.
 *
 * <p>Two implementations: one for any OpenAI-compatible {@code /embeddings} endpoint (OpenAI,
 * Ollama, Docker Model Runner) and one used when the feature is disabled, so that the rest of
 * the application never has to check a flag before calling it.
 */
public interface EmbeddingClient {

    /** Whether the provider is configured ({@code VIZ_EMBEDDING_ENABLED=true}). */
    boolean enabled();

    /**
     * Embeds one text.
     *
     * @throws ExplorerException with an {@code EMBEDDING_*} code when the provider fails or
     *     the feature is disabled
     */
    EmbeddingResult embed(String text);

    /** Fails fast with {@code EMBEDDING_DISABLED} when no provider is configured. */
    default void requireEnabled() {
        if (!enabled()) {
            throw disabledError();
        }
    }

    /** The error of a call made while the RAG simulation is disabled. */
    static ExplorerException disabledError() {
        return new ExplorerException(
                ErrorCode.EMBEDDING_DISABLED,
                "The RAG simulation is disabled: set VIZ_EMBEDDING_ENABLED=true and configure the provider.");
    }
}
