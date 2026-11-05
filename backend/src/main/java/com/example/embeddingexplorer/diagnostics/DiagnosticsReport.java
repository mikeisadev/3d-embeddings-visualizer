package com.example.embeddingexplorer.diagnostics;

import java.time.Instant;
import java.util.List;

/**
 * Response of {@code GET /api/v1/diagnostics}. Always returned with HTTP 200: failures are
 * reported by the individual checks.
 *
 * @param status    {@code FAIL} if any check failed, else {@code WARN} if any warned, else {@code OK}
 * @param checks    the checks, in execution order
 * @param checkedAt when the checks ran
 */
public record DiagnosticsReport(CheckStatus status, List<DiagnosticCheck> checks, Instant checkedAt) {

    public DiagnosticsReport {
        checks = List.copyOf(checks);
    }

    static DiagnosticsReport of(List<DiagnosticCheck> checks, Instant checkedAt) {
        return new DiagnosticsReport(overall(checks), checks, checkedAt);
    }

    private static CheckStatus overall(List<DiagnosticCheck> checks) {
        if (checks.stream().anyMatch(check -> check.status() == CheckStatus.FAIL)) {
            return CheckStatus.FAIL;
        }
        if (checks.stream().anyMatch(check -> check.status() == CheckStatus.WARN)) {
            return CheckStatus.WARN;
        }
        return CheckStatus.OK;
    }
}
