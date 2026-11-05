package com.example.embeddingexplorer.config;

import java.util.regex.Pattern;

/**
 * A schema or table name that can be safely written into SQL text.
 *
 * <p>Identifiers cannot be bound as JDBC parameters, so they are the only part of a query
 * built from strings. They come exclusively from validated configuration, never from a
 * request: the pattern allows only lowercase letters, digits and underscores, so the quoted
 * form can never break out of its double quotes.
 *
 * @param value the raw identifier, for example {@code vector_store}
 */
public record SqlIdentifier(String value) {

    /** Lowercase PostgreSQL identifier, at most 63 characters (the PostgreSQL limit). */
    public static final String PATTERN = "^[a-z_][a-z0-9_]{0,62}$";

    /** Validation message shared by the configuration properties. */
    public static final String MESSAGE =
            "must be a lowercase SQL identifier: a letter or underscore followed by letters, digits"
                    + " or underscores, at most 63 characters";

    private static final Pattern VALID = Pattern.compile(PATTERN);

    public SqlIdentifier {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier '%s': %s".formatted(value, MESSAGE));
        }
    }

    /** Returns the double-quoted identifier, ready to be embedded in SQL text. */
    public String quoted() {
        return '"' + value + '"';
    }
}
