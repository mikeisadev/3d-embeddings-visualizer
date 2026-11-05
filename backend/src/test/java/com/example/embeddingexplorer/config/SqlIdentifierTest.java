package com.example.embeddingexplorer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SqlIdentifierTest {

    @ParameterizedTest
    @ValueSource(strings = {"vector_store", "vector_store_bge_m3", "_private", "t1", "public", "user"})
    void acceptsLowercaseIdentifiers(String value) {
        assertThat(new SqlIdentifier(value).quoted()).isEqualTo('"' + value + '"');
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "Vector_Store",
        "1table",
        "vector-store",
        "vector store",
        "vector_store; DROP TABLE x",
        "tab\"le",
        "schema.table",
        "ünicode",
        "a234567890123456789012345678901234567890123456789012345678901234"
    })
    void rejectsAnythingElse(String value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SqlIdentifier(value))
                .withMessageContaining("Invalid SQL identifier");
    }

    @Test
    void acceptsTheMaximumPostgresLength() {
        String sixtyThree = "a".repeat(63);
        assertThat(new SqlIdentifier(sixtyThree).value()).hasSize(63);
    }

    @Test
    void tableRefQuotesBothParts() {
        TableRef table = TableRef.of("public", "vector_store");
        assertThat(table.sql()).isEqualTo("\"public\".\"vector_store\"");
        assertThat(table.displayName()).isEqualTo("public.vector_store");
    }
}
