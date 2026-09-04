package ai.docuforge.security.ratelimit;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.ErrorResponse;
import ai.docuforge.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!properties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/auth/login")) {
            if (!tryConsume("login:" + clientKey(request), properties.loginPerMinute())) {
                writeTooMany(response, "Trop de tentatives de connexion. Reessayez plus tard.");
                return;
            }
        } else if (path.startsWith("/api/v1/ai/") && !"GET".equalsIgnoreCase(method)) {
            if (!tryConsume("ai:" + principalOrIp(request), properties.aiPerMinute())) {
                writeTooMany(response, "Limite d'appels IA atteinte. Reessayez plus tard.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean tryConsume(String key, int perMinute) {
        int capacity = Math.max(perMinute, 1);
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofMinutes(1))
                        .build())
                .build());
        return bucket.tryConsume(1);
    }

    private String principalOrIp(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof DocuForgePrincipal principal) {
            return principal.getUserId().toString();
        }
        return clientKey(request);
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private void writeTooMany(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                Instant.now(),
                429,
                "RATE_LIMITED",
                message,
                List.of(),
                MDC.get("traceId")
        ));
    }
}
