package com.example.embeddingexplorer.diagnostics;

/** Outcome of one diagnostics check. */
public enum CheckStatus {
    /** Everything as expected. */
    OK,
    /** Works, but something deserves attention (empty table, missing index…). */
    WARN,
    /** Blocks the tool; {@code code} says why. */
    FAIL,
    /** Not run, because a check it depends on did not pass or there was nothing to check. */
    SKIPPED
}
