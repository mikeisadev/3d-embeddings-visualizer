package com.example.embeddingexplorer.diagnostics;

import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.WarningCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Result of one diagnostics check.
 *
 * @param id     stable check id, for example {@code db.connection}
 * @param status outcome
 * @param detail technical explanation in English (the UI adds an Italian hint from {@code code})
 * @param code   an {@link ErrorCode} for {@code FAIL}, a {@link WarningCode} for {@code WARN}, otherwise usually {@code null}
 * @param facts  what the check found, for example the server version
 */
public record DiagnosticCheck(String id, CheckStatus status, String detail, String code, Map<String, Object> facts) {

    public DiagnosticCheck {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detail, "detail");
        facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }

    static DiagnosticCheck ok(String id, String detail, Map<String, Object> facts) {
        return new DiagnosticCheck(id, CheckStatus.OK, detail, null, facts);
    }

    static DiagnosticCheck warn(String id, WarningCode code, String detail, Map<String, Object> facts) {
        return new DiagnosticCheck(id, CheckStatus.WARN, detail, code.name(), facts);
    }

    static DiagnosticCheck fail(String id, ErrorCode code, String detail, Map<String, Object> facts) {
        return new DiagnosticCheck(id, CheckStatus.FAIL, detail, code.name(), facts);
    }

    static DiagnosticCheck skipped(String id, String detail) {
        return new DiagnosticCheck(id, CheckStatus.SKIPPED, detail, null, Map.of());
    }

    /** Whether checks that depend on this one can run. */
    boolean passed() {
        return status == CheckStatus.OK || status == CheckStatus.WARN;
    }

    /** Builds an ordered facts map from alternating keys and values; values may be {@code null}. */
    static Map<String, Object> facts(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Facts need key-value pairs");
        }
        Map<String, Object> facts = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            facts.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return facts;
    }
}
