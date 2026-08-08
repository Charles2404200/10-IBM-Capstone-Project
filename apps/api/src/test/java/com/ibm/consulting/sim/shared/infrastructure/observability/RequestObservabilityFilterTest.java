package com.ibm.consulting.sim.shared.infrastructure.observability;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestObservabilityFilterTest {

    private final RequestObservabilityFilter filter = new RequestObservabilityFilter();

    @Test
    void returnsSuppliedSafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/engagements");
        request.addHeader(RequestObservabilityFilter.REQUEST_ID_HEADER, "web-01.request_42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertThat(response.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER))
                .isEqualTo("web-01.request_42");
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/engagements");
        request.addHeader(RequestObservabilityFilter.REQUEST_ID_HEADER, "line-one\nline-two");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertThat(UUID.fromString(response.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER)))
                .isNotNull();
    }
}