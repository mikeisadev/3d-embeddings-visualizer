package com.example.embeddingexplorer.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.UncategorizedSQLException;

class DatabaseErrorTranslatorTest {

    @ParameterizedTest
    @CsvSource({
        "08001, DB_UNAVAILABLE",
        "08006, DB_UNAVAILABLE",
        "28P01, DB_AUTH_FAILED",
        "28000, DB_AUTH_FAILED",
        "42501, DB_PERMISSION_DENIED",
        "3D000, DB_NOT_FOUND",
        "42P01, TABLE_NOT_FOUND",
        "3F000, TABLE_NOT_FOUND",
        "42703, TABLE_SCHEMA_INVALID",
        "42704, PGVECTOR_MISSING",
        "42883, PGVECTOR_MISSING",
        "57014, DB_TIMEOUT",
        "57P01, DB_UNAVAILABLE",
        "57P03, DB_UNAVAILABLE",
        "53300, DB_UNAVAILABLE"
    })
    void mapsSqlStates(String sqlState, ErrorCode expected) {
        SQLException driverError = new SQLException("driver message", sqlState);
        BadSqlGrammarException springError = new BadSqlGrammarException("query", "SELECT 1", driverError);

        assertThat(DatabaseErrorTranslator.errorCode(springError)).contains(expected);
    }

    @Test
    void findsTheSqlStateDeepInTheCauseChain() {
        SQLException driverError = new SQLException("Connection refused", "08001");
        SQLTransientConnectionException poolError =
                new SQLTransientConnectionException("viz-db - Connection is not available", null, driverError);
        CannotGetJdbcConnectionException springError =
                new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", poolError);

        assertThat(DatabaseErrorTranslator.sqlState(springError)).contains("08001");
        assertThat(DatabaseErrorTranslator.errorCode(springError)).contains(ErrorCode.DB_UNAVAILABLE);
    }

    @Test
    void fallsBackToConnectionExceptionTypesWithoutSqlState() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("down", new ConnectException("Connection refused"));

        assertThat(DatabaseErrorTranslator.errorCode(failure)).contains(ErrorCode.DB_UNAVAILABLE);
    }

    @Test
    void treatsAPoolTimeoutWithoutSqlStateAsUnavailable() {
        CannotGetJdbcConnectionException failure = new CannotGetJdbcConnectionException(
                "Failed", new SQLTransientConnectionException("Connection is not available, request timed out"));

        assertThat(DatabaseErrorTranslator.errorCode(failure)).contains(ErrorCode.DB_UNAVAILABLE);
    }

    @Test
    void returnsEmptyForUnknownFailures() {
        UncategorizedSQLException failure =
                new UncategorizedSQLException("query", "SELECT 1", new SQLException("division by zero", "22012"));

        assertThat(DatabaseErrorTranslator.errorCode(failure)).isEmpty();
        assertThat(DatabaseErrorTranslator.errorCode(new IllegalStateException("bug"))).isEmpty();
    }

    @Test
    void translatesIntoASafeExceptionWithoutDriverMessages() {
        SQLException driverError =
                new SQLException("FATAL: password authentication failed for user \"chatbot\"", "28P01");

        ExplorerException translated = DatabaseErrorTranslator.translate(
                        new CannotGetJdbcConnectionException("Failed", driverError))
                .orElseThrow();

        assertThat(translated.code()).isEqualTo(ErrorCode.DB_AUTH_FAILED);
        assertThat(translated.getMessage()).contains("SQLSTATE 28P01").doesNotContain("FATAL");
        assertThat(translated.properties()).containsEntry("sqlState", "28P01");
    }

    @Test
    void survivesCyclicCauseChains() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);

        assertThat(DatabaseErrorTranslator.errorCode(first)).isEmpty();
    }
}
