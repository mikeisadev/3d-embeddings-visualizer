package com.example.embeddingexplorer.dataset;

import java.util.StringJoiner;

/** The pgvector text format, used to bind a vector as {@code CAST(? AS vector)}. */
public final class PgVectors {

    private PgVectors() {}

    /** For example {@code [0.1,-0.2,0.3]}; {@link Float#toString} round-trips every value exactly. */
    public static String literal(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }
}
