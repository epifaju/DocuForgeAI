package ai.docuforge.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.i18n.ErrorMessages;
import ai.docuforge.config.RateLimitProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.LocaleResolver;

class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private ErrorMessages errorMessages;
    private LocaleResolver localeResolver;

    @BeforeEach
    void setUp() {
        errorMessages = mock(ErrorMessages.class);
        localeResolver = mock(LocaleResolver.class);
        when(localeResolver.resolveLocale(any())).thenReturn(Locale.FRENCH);
        when(errorMessages.msg(any(), any(Locale.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledBypassesLimiting() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties(false, 1, 1, 1));
        AtomicInteger calls = new AtomicInteger();
        FilterChain chain = (req, res) -> calls.incrementAndGet();

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(loginRequest("1.1.1.1"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        assertThat(calls.get()).isEqualTo(5);
    }

    @Test
    void loginBucketReturns429WithRateLimitedCode() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties(true, 1, 10, 10));
        FilterChain chain = (req, res) -> {
        };

        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilter(loginRequest("10.0.0.1"), ok, chain);
        assertThat(ok.getStatus()).isNotEqualTo(429);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(loginRequest("10.0.0.1"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
        JsonNode body = objectMapper.readTree(blocked.getContentAsByteArray());
        assertThat(body.path("code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(body.path("message").asText()).isEqualTo("error.rate_limit.login");
    }

    @Test
    void forgotPasswordUsesSeparateBucketAndXffClientKey() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties(true, 100, 100, 1));
        FilterChain chain = (req, res) -> {
        };

        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/api/v1/auth/forgot-password");
        first.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilter(first, ok, chain);
        assertThat(ok.getStatus()).isNotEqualTo(429);

        MockHttpServletRequest second = new MockHttpServletRequest("POST", "/api/v1/auth/forgot-password");
        second.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(second, blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);

        MockHttpServletRequest otherIp = new MockHttpServletRequest("POST", "/api/v1/auth/forgot-password");
        otherIp.addHeader("X-Forwarded-For", "198.51.100.20");
        MockHttpServletResponse allowed = new MockHttpServletResponse();
        filter.doFilter(otherIp, allowed, chain);
        assertThat(allowed.getStatus()).isNotEqualTo(429);
    }

    @Test
    void aiBucketKeysByPrincipalWhenAuthenticated() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties(true, 100, 1, 100));
        FilterChain chain = (req, res) -> {
        };
        UUID userId = UUID.randomUUID();
        DocuForgePrincipal principal = new DocuForgePrincipal(
                userId, UUID.randomUUID(), "co", "u@co.test", "h", true, Set.of("ADMIN")
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/ai/rewrite");
        req1.setRemoteAddr("1.1.1.1");
        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilter(req1, ok, chain);
        assertThat(ok.getStatus()).isNotEqualTo(429);

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/ai/rewrite");
        req2.setRemoteAddr("9.9.9.9");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(req2, blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    void aiBucketFallsBackToIpWhenAnonymous() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties(true, 100, 1, 100));
        FilterChain chain = (req, res) -> {
        };

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/ai/generate");
        req1.setRemoteAddr("5.5.5.5");
        filter.doFilter(req1, new MockHttpServletResponse(), chain);

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/ai/generate");
        req2.setRemoteAddr("5.5.5.5");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(req2, blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);

        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/v1/ai/generate");
        other.setRemoteAddr("6.6.6.6");
        MockHttpServletResponse allowed = new MockHttpServletResponse();
        filter.doFilter(other, allowed, chain);
        assertThat(allowed.getStatus()).isNotEqualTo(429);
    }

    private RateLimitFilter filter(RateLimitProperties properties) {
        return new RateLimitFilter(properties, objectMapper, errorMessages, localeResolver);
    }

    private static MockHttpServletRequest loginRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(ip);
        return request;
    }
}
