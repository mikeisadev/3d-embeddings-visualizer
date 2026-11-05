package com.example.embeddingexplorer.error;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

/**
 * Translates database failures into stable {@link ErrorCode}s.
 *
 * <p>The PostgreSQL SQLSTATE is the most reliable signal, so the whole cause chain is
 * searched for it (Spring and HikariCP wrap the driver exception). When no SQLSTATE is
 * available, connection and timeout exception types are used as a fallback. Failures that
 * match nothing are not environment problems: the caller treats them as unexpected.
 */
public final class DatabaseErrorTranslator {

    private static final int MAX_CAUSE_DEPTH = 32;

    private DatabaseErrorTranslator() {}

    /** Translates a failure into an {@link ExplorerException} with a safe detail, when its code is known. */
    public static Optional<ExplorerException> translate(Throwable failure) {
        return errorCode(failure).map(code -> {
            Optional<String> sqlState = sqlState(failure);
            Map<String, Object> properties = sqlState.<Map<String, Object>>map(state -> Map.of("sqlState", state))
                    .orElse(Map.of());
            return new ExplorerException(code, detail(code, sqlState.orElse(null)), properties, failure);
        });
    }

    /** The error code of a database failure, or empty when it is not a known environment problem. */
    public static Optional<ErrorCode> errorCode(Throwable failure) {
        Optional<ErrorCode> fromSqlState = sqlState(failure).flatMap(DatabaseErrorTranslator::fromSqlState);
        if (fromSqlState.isPresent()) {
            return fromSqlState;
        }
        if (causes(failure).anyMatch(DatabaseErrorTranslator::isTimeout)) {
            return Optional.of(ErrorCode.DB_TIMEOUT);
        }
        if (causes(failure).anyMatch(DatabaseErrorTranslator::isConnectivityFailure)) {
            return Optional.of(ErrorCode.DB_UNAVAILABLE);
        }
        return Optional.empty();
    }

    /** The first SQLSTATE found in the cause chain. */
    public static Optional<String> sqlState(Throwable failure) {
        return causes(failure)
                .filter(SQLException.class::isInstance)
                .map(cause -> ((SQLException) cause).getSQLState())
                .filter(state -> state != null && !state.isBlank())
                .findFirst();
    }

    /**
     * Maps a PostgreSQL SQLSTATE to an error code. Classes {@code 08} (connection exception)
     * and {@code 57P0x} (server shutting down or starting up) both mean "not reachable now".
     */
    static Optional<ErrorCode> fromSqlState(String sqlState) {
        if (sqlState.startsWith("08")) {
            return Optional.of(ErrorCode.DB_UNAVAILABLE);
        }
        return Optional.ofNullable(switch (sqlState) {
            case "28P01", "28000" -> ErrorCode.DB_AUTH_FAILED;
            case "42501" -> ErrorCode.DB_PERMISSION_DENIED;
            case "3D000" -> ErrorCode.DB_NOT_FOUND;
            case "42P01", "3F000" -> ErrorCode.TABLE_NOT_FOUND;
            case "42703" -> ErrorCode.TABLE_SCHEMA_INVALID;
            case "42704", "42883" -> ErrorCode.PGVECTOR_MISSING;
            case "57014" -> ErrorCode.DB_TIMEOUT;
            case "57P01", "57P02", "57P03", "53300" -> ErrorCode.DB_UNAVAILABLE;
            default -> null;
        });
    }

    static String detail(ErrorCode code, String sqlState) {
        String detail = switch (code) {
            case DB_UNAVAILABLE -> "The vector database is not reachable.";
            case DB_AUTH_FAILED -> "The database rejected the configured user or password.";
            case DB_PERMISSION_DENIED -> "The database user is not allowed to read the vector table.";
            case DB_NOT_FOUND -> "The database named in the JDBC URL does not exist.";
            case TABLE_NOT_FOUND -> "The configured schema or table does not exist.";
            case TABLE_SCHEMA_INVALID -> "The vector table does not have the expected columns.";
            case PGVECTOR_MISSING -> "The pgvector type or operators are not available in the database.";
            case DB_TIMEOUT -> "The database query exceeded the statement timeout.";
            default -> "The database query failed.";
        };
        return sqlState == null ? detail : detail + " (SQLSTATE " + sqlState + ")";
    }

    private static boolean isTimeout(Throwable cause) {
        return cause instanceof QueryTimeoutException
                || cause instanceof SQLTimeoutException
                || cause instanceof SocketTimeoutException;
    }

    private static boolean isConnectivityFailure(Throwable cause) {
        return cause instanceof CannotGetJdbcConnectionException
                || cause instanceof SQLTransientConnectionException
                || cause instanceof ConnectException
                || cause instanceof NoRouteToHostException
                || cause instanceof UnknownHostException;
    }

    /** The failure and its causes, protected against cycles. */
    private static Stream<Throwable> causes(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        return Stream.iterate(failure, cause -> cause != null && seen.add(cause), Throwable::getCause)
                .limit(MAX_CAUSE_DEPTH);
    }
}
