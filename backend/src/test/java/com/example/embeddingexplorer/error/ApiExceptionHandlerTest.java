package com.example.embeddingexplorer.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ApiExceptionHandlerTest.ThrowingController.class)
@Import({ApiExceptionHandlerTest.ThrowingController.class, ApiExceptionHandlerTest.FixedClock.class})
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void explorerExceptionsBecomeProblemDetails() throws Exception {
        mvc.perform(get("/test/not-found").header(RequestIdFilter.HEADER, "req-12345678"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("CHUNK_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Chunk abc does not exist."))
                .andExpect(jsonPath("$.requestId").value("req-12345678"))
                .andExpect(jsonPath("$.timestamp").value("2026-01-01T10:00:00Z"))
                .andExpect(header().string(RequestIdFilter.HEADER, "req-12345678"));
    }

    @Test
    void extraPropertiesAndRetryAfterAreAdded() throws Exception {
        mvc.perform(get("/test/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(6))
                .andExpect(header().string("Retry-After", "6"));
    }

    @Test
    void databaseFailuresAreTranslatedBySqlState() throws Exception {
        mvc.perform(get("/test/database-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DB_UNAVAILABLE"))
                .andExpect(jsonPath("$.sqlState").value("08001"))
                .andExpect(content().string(not(containsString("secret-host"))));
    }

    @Test
    void unexpectedErrorsHideTheirDetails() throws Exception {
        mvc.perform(get("/test/bug"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(content().string(not(containsString("password=hunter2"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void invalidParametersAreValidationErrors() throws Exception {
        mvc.perform(get("/test/neighbors").param("k", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("k"));
        mvc.perform(get("/test/neighbors").param("k", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void invalidBodiesAreValidationErrorsWithoutRejectedValues() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"question\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("question"));
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownPathsAndMethodsHaveTheirOwnCodes() throws Exception {
        mvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/test/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/not-found")
        String notFound() {
            throw new ExplorerException(ErrorCode.CHUNK_NOT_FOUND, "Chunk abc does not exist.");
        }

        @GetMapping("/test/rate-limited")
        String rateLimited() {
            throw new ExplorerException(
                    ErrorCode.RATE_LIMITED,
                    "Too many searches.",
                    Map.of(ExplorerException.RETRY_AFTER_SECONDS, 6));
        }

        @GetMapping("/test/database-down")
        String databaseDown() {
            throw new CannotGetJdbcConnectionException(
                    "Failed", new SQLException("Connection to secret-host:5432 refused", "08001"));
        }

        @GetMapping("/test/bug")
        String bug() {
            throw new IllegalStateException("password=hunter2");
        }

        @GetMapping("/test/neighbors")
        String neighbors(@RequestParam @Max(50) int k) {
            return "ok " + k;
        }

        @PostMapping("/test/body")
        String body(@Valid @RequestBody Question question) {
            return question.question();
        }
    }

    record Question(@NotBlank String question) {}

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        }
    }
}
