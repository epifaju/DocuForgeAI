package ai.docuforge.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docuforge.config.AuthCookieProperties;
import ai.docuforge.config.DocuForgeProperties;
import ai.docuforge.config.JwtProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookieServiceTest {

    private static final JwtProperties JWT = new JwtProperties(
            "test-secret-key-with-at-least-32-characters!!", 900, 604_800
    );

    @Test
    void disabledIsNoOpForWriteAndClear() {
        AuthCookieService service = service(false, false, "local");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.writeTokens(response, "access", "refresh");
        service.clear(response);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(service.enabled()).isFalse();
    }

    @Test
    void writeTokensSetsHttpOnlyPathAndSameSite() {
        AuthCookieService service = service(true, false, "local");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.writeTokens(response, "access-token", "refresh-token");

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2);

        String access = cookies.stream().filter(c -> c.startsWith("df_access=")).findFirst().orElseThrow();
        String refresh = cookies.stream().filter(c -> c.startsWith("df_refresh=")).findFirst().orElseThrow();

        assertThat(access).contains("access-token");
        assertThat(access).containsIgnoringCase("HttpOnly");
        assertThat(access).contains("Path=/");
        assertThat(access).contains("SameSite=Lax");
        assertThat(access).contains("Max-Age=900");
        assertThat(access).doesNotContain("Secure");

        assertThat(refresh).contains("refresh-token");
        assertThat(refresh).contains("Path=/api/v1/auth");
        assertThat(refresh).contains("Max-Age=604800");
    }

    @Test
    void productionForcesSecureFlag() {
        AuthCookieService service = service(true, false, "production");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.writeTokens(response, "a", "r");

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .allSatisfy(c -> assertThat(c).containsIgnoringCase("Secure"));
    }

    @Test
    void readAccessAndRefreshIgnoreBlankAndMissing() {
        AuthCookieService service = service(true, false, "local");

        MockHttpServletRequest missing = new MockHttpServletRequest();
        assertThat(service.readAccess(missing)).isEmpty();
        assertThat(service.readRefresh(missing)).isEmpty();

        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.setCookies(
                new jakarta.servlet.http.Cookie("df_access", "  "),
                new jakarta.servlet.http.Cookie("df_refresh", "refresh-value")
        );
        assertThat(service.readAccess(blank)).isEmpty();
        assertThat(service.readRefresh(blank)).contains("refresh-value");
    }

    @Test
    void clearExpiresCookies() {
        AuthCookieService service = service(true, false, "local");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clear(response);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .allSatisfy(c -> assertThat(c).contains("Max-Age=0"));
    }

    private static AuthCookieService service(boolean enabled, boolean secure, String env) {
        return new AuthCookieService(
                new AuthCookieProperties(enabled, "df_access", "df_refresh", "Lax", secure, "/"),
                JWT,
                new DocuForgeProperties(env, "http://localhost:5174", null)
        );
    }
}
