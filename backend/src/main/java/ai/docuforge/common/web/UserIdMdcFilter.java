package ai.docuforge.common.web;

import ai.docuforge.auth.security.DocuForgePrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates MDC userId after Spring Security authentication (never logs tokens/secrets).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 30)
public class UserIdMdcFilter extends OncePerRequestFilter {

    public static final String USER_ID_MDC_KEY = "userId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof DocuForgePrincipal principal) {
                MDC.put(USER_ID_MDC_KEY, principal.getUserId().toString());
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ID_MDC_KEY);
        }
    }
}
