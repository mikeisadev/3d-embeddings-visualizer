package com.example.embeddingexplorer.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void reusesAValidIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dataset");
        request.addHeader(RequestIdFilter.HEADER, "web-1a2b3c4d");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idInMdc = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                idInMdc.set(MDC.get(RequestIdFilter.MDC_KEY));
            }
        });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("web-1a2b3c4d");
        assertThat(idInMdc.get()).isEqualTo("web-1a2b3c4d");
        assertThat(RequestIdFilter.requestIdOf(request)).isEqualTo("web-1a2b3c4d");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "with space inside", "line\nbreak-injection", "0123456789012345678901234567890123456789012345678901234567890123456789"})
    void replacesInvalidIds(String invalid) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dataset");
        request.addHeader(RequestIdFilter.HEADER, invalid);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotEqualTo(invalid).hasSize(36);
    }

    @Test
    void generatesAnIdWhenMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).matches("[0-9a-f-]{36}");
    }
}
