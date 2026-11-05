package com.example.embeddingexplorer.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 {@link ProblemDetail} with three extra members:
 * {@code code} (a stable {@link ErrorCode}), {@code requestId} and {@code timestamp}.
 *
 * <p>Responses never contain stack traces, SQL or secrets. Errors are logged once, here at
 * the boundary: bad requests at INFO without their content, environment problems (database
 * or provider down) at WARN without a stack trace, and anything unexpected at ERROR with the
 * stack trace. The MDC carries the request id into every line.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String UNEXPECTED_DETAIL =
            "An unexpected error occurred. Quote the request id when reporting the problem.";

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ExplorerException.class)
    ResponseEntity<Object> handleExplorerException(ExplorerException failure, HttpServletRequest request) {
        logFailure(failure.code(), failure, request);
        ProblemDetail problem = problem(failure.code(), failure.getMessage(), request);
        failure.properties().forEach(problem::setProperty);
        HttpHeaders headers = new HttpHeaders();
        if (failure.properties().get(ExplorerException.RETRY_AFTER_SECONDS) instanceof Number seconds) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(seconds.longValue()));
        }
        return new ResponseEntity<>(problem, headers, failure.code().status());
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Object> handleDataAccessException(DataAccessException failure, HttpServletRequest request) {
        return DatabaseErrorTranslator.translate(failure)
                .map(translated -> handleExplorerException(translated, request))
                .orElseGet(() -> handleUnexpected(failure, request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception failure, HttpServletRequest request) {
        logFailure(ErrorCode.INTERNAL_ERROR, failure, request);
        ProblemDetail problem = problem(ErrorCode.INTERNAL_ERROR, UNEXPECTED_DETAIL, request);
        return new ResponseEntity<>(problem, ErrorCode.INTERNAL_ERROR.status());
    }

    /**
     * Enriches the problems Spring MVC creates for its own exceptions (validation, unknown
     * path, wrong method, unreadable body…) so that they carry an {@link ErrorCode} too.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception failure,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest webRequest) {
        ResponseEntity<Object> response =
                super.handleExceptionInternal(failure, body, headers, statusCode, webRequest);
        HttpServletRequest request = servletRequest(webRequest);
        if (response == null || request == null) {
            return response;
        }
        ErrorCode code = codeForFrameworkStatus(statusCode);
        String detail = response.getBody() instanceof ProblemDetail original && original.getDetail() != null
                ? original.getDetail()
                : code.status().getReasonPhrase();
        ProblemDetail problem = problem(code, detail, request);
        List<Map<String, String>> errors = validationErrors(failure);
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        logFailure(code, failure, request);
        return new ResponseEntity<>(problem, response.getHeaders(), code.status());
    }

    private ProblemDetail problem(ErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setProperty("code", code.name());
        problem.setProperty("requestId", RequestIdFilter.requestIdOf(request));
        problem.setProperty("timestamp", clock.instant().toString());
        return problem;
    }

    private static ErrorCode codeForFrameworkStatus(HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCode.NOT_FOUND;
        }
        if (status.value() == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            return ErrorCode.METHOD_NOT_ALLOWED;
        }
        return status.is4xxClientError() ? ErrorCode.VALIDATION_FAILED : ErrorCode.INTERNAL_ERROR;
    }

    /** Field and parameter messages, without the rejected values (they may contain user text). */
    private static List<Map<String, String>> validationErrors(Exception failure) {
        if (failure instanceof MethodArgumentNotValidException invalidBody) {
            return invalidBody.getBindingResult().getFieldErrors().stream()
                    .map(error -> error(error.getField(), error))
                    .toList();
        }
        if (failure instanceof HandlerMethodValidationException invalidParameters) {
            return invalidParameters.getParameterValidationResults().stream()
                    .flatMap(result -> {
                        String name = result.getMethodParameter().getParameterName();
                        return result.getResolvableErrors().stream().map(error -> error(name, error));
                    })
                    .toList();
        }
        return List.of();
    }

    private static Map<String, String> error(@Nullable String field, MessageSourceResolvable error) {
        String name = field != null ? field : error instanceof FieldError fieldError ? fieldError.getField() : "request";
        String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : "is invalid";
        return Map.of("field", name, "message", message);
    }

    private static void logFailure(ErrorCode code, Throwable failure, HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("Unexpected error on {} {}", method, path, failure);
        } else if (code.isEnvironmentProblem()) {
            log.warn("{} {} failed with {}: {}", method, path, code, safeMessage(failure));
        } else {
            log.info("{} {} rejected with {}", method, path, code);
        }
    }

    /** Only our own messages are known to be safe; framework messages may quote user input. */
    private static String safeMessage(Throwable failure) {
        return Stream.iterate(failure, cause -> cause != null, Throwable::getCause)
                .limit(8)
                .filter(ExplorerException.class::isInstance)
                .map(Throwable::getMessage)
                .findFirst()
                .orElse(failure.getClass().getSimpleName());
    }

    private static @Nullable HttpServletRequest servletRequest(WebRequest request) {
        return request instanceof NativeWebRequest nativeRequest
                ? nativeRequest.getNativeRequest(HttpServletRequest.class)
                : null;
    }
}
