package com.example.embeddingexplorer.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An error with a stable {@link ErrorCode}, turned into an RFC 9457 problem response by
 * {@link ApiExceptionHandler}.
 *
 * <p>The message is the problem {@code detail}: it is shown to the user, so it must be
 * technical but safe (English, no SQL, no secrets, no chunk text). Extra properties, such as
 * the dimensions found, become additional members of the problem document.
 */
public class ExplorerException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> properties;

    public ExplorerException(ErrorCode code, String detail) {
        this(code, detail, Map.of(), null);
    }

    public ExplorerException(ErrorCode code, String detail, Throwable cause) {
        this(code, detail, Map.of(), cause);
    }

    public ExplorerException(ErrorCode code, String detail, Map<String, ?> properties) {
        this(code, detail, properties, null);
    }

    public ExplorerException(ErrorCode code, String detail, Map<String, ?> properties, Throwable cause) {
        super(Objects.requireNonNull(detail, "detail"), cause);
        this.code = Objects.requireNonNull(code, "code");
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public ErrorCode code() {
        return code;
    }

    /** Additional, safe members of the problem document. */
    public Map<String, Object> properties() {
        return properties;
    }
}
