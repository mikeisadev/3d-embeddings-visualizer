package com.example.embeddingexplorer.error;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes returned in the {@code code} property of every error response.
 *
 * <p>The frontend maps each code to an Italian message and hint, so codes are part of the
 * public API: never rename them. Each code has exactly one HTTP status.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CHUNK_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    DATASET_CHANGED(HttpStatus.CONFLICT),
    DIMENSION_MISMATCH(HttpStatus.CONFLICT),
    PROJECTION_NOT_POSSIBLE(HttpStatus.UNPROCESSABLE_CONTENT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    EMBEDDING_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    EMBEDDING_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY),
    EMBEDDING_AUTH_FAILED(HttpStatus.BAD_GATEWAY),
    EMBEDDING_MODEL_NOT_FOUND(HttpStatus.BAD_GATEWAY),
    DB_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    DB_AUTH_FAILED(HttpStatus.SERVICE_UNAVAILABLE),
    DB_PERMISSION_DENIED(HttpStatus.SERVICE_UNAVAILABLE),
    DB_NOT_FOUND(HttpStatus.SERVICE_UNAVAILABLE),
    PGVECTOR_MISSING(HttpStatus.SERVICE_UNAVAILABLE),
    TABLE_NOT_FOUND(HttpStatus.SERVICE_UNAVAILABLE),
    TABLE_SCHEMA_INVALID(HttpStatus.SERVICE_UNAVAILABLE),
    SNAPSHOT_TOO_LARGE(HttpStatus.SERVICE_UNAVAILABLE),
    EMBEDDING_DISABLED(HttpStatus.SERVICE_UNAVAILABLE),
    DB_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    EMBEDDING_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    /**
     * Whether the code describes a problem of the environment (database, provider,
     * configuration of the data) rather than a bad request or a bug. Such errors are
     * expected in a classroom and are logged as warnings without a stack trace.
     */
    public boolean isEnvironmentProblem() {
        return status.is5xxServerError() && this != INTERNAL_ERROR;
    }
}
