package ai.docuforge.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void propagatesIncomingTraceIdAndClearsMdc() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "incoming-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> during = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            during.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
        });

        assertThat(during.get()).isEqualTo("incoming-trace");
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("incoming-trace");
        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void generatesUuidWhenMissingAndClearsEvenOnError() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (FilterChain) (req, res) -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isNotBlank();
        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }
}
