package com.example.embeddingexplorer.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a {@code requestId}, so that an error seen by a student can be found
 * in the logs. A valid incoming {@code X-Request-Id} is reused (the frontend sends one);
 * otherwise a new one is generated. The id is put in the MDC, in a request attribute and in
 * the response header.
 *
 * <p>Only short ids made of safe characters are accepted, which also prevents log injection.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** The id of the current request, or a fresh one if the filter did not run. */
    public static String requestIdOf(ServletRequest request) {
        Object id = request.getAttribute(ATTRIBUTE);
        return id instanceof String value ? value : newId();
    }

    static String resolve(String candidate) {
        return candidate != null && VALID_ID.matcher(candidate).matches() ? candidate : newId();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
