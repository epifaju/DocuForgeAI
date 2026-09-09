package ai.docuforge.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.auth.security.DocuForgePrincipal;
import jakarta.servlet.FilterChain;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class UserIdMdcFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void setsUserIdWhenPrincipalPresentAndClearsAfter() throws Exception {
        UUID userId = UUID.randomUUID();
        DocuForgePrincipal principal = new DocuForgePrincipal(
                userId, UUID.randomUUID(), "co", "u@co.test", "h", true, Set.of("USER")
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        UserIdMdcFilter filter = new UserIdMdcFilter();
        AtomicReference<String> during = new AtomicReference<>();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
            during.set(MDC.get(UserIdMdcFilter.USER_ID_MDC_KEY));
        });

        assertThat(during.get()).isEqualTo(userId.toString());
        assertThat(MDC.get(UserIdMdcFilter.USER_ID_MDC_KEY)).isNull();
    }

    @Test
    void skipsAnonymousAndClearsOnException() throws Exception {
        UserIdMdcFilter filter = new UserIdMdcFilter();
        AtomicReference<String> during = new AtomicReference<>("sentinel");
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
            during.set(MDC.get(UserIdMdcFilter.USER_ID_MDC_KEY));
        });
        assertThat(during.get()).isNull();

        DocuForgePrincipal principal = new DocuForgePrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "co", "u@co.test", "h", true, Set.of("USER")
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (FilterChain) (req, res) -> {
                    throw new IllegalStateException("boom");
                }
        )).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(UserIdMdcFilter.USER_ID_MDC_KEY)).isNull();
    }
}
