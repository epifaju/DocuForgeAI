package ai.docuforge.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.domain.audit.AuditLogRepository;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthCookieSecurityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine")
            .withDatabaseName("docuforge")
            .withUsername("docuforge")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("docuforge.bootstrap.enabled", () -> "false");
        registry.add("docuforge.jwt.secret", () -> "test-secret-key-with-at-least-32-characters!!");
        registry.add("docuforge.auth.cookies.enabled", () -> "true");
        registry.add("docuforge.auth.cookies.secure", () -> "false");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        userAccountRepository.deleteAll();
        companyRepository.deleteAll();

        Company company = new Company();
        company.setName("Cookie Co");
        company.setIdentifier("cookie-co");
        company = companyRepository.save(company);

        saveUser(company, "admin@cookie-co.test", "AdminPass123!", RoleCode.ADMIN);
        saveUser(company, "viewer@cookie-co.test", "ViewerPass123!", RoleCode.VIEWER);
    }

    @Test
    void loginSetsHttpOnlyCookiesAndMeWorksWithAccessCookieAlone() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"cookie-co","email":"admin@cookie-co.test","password":"AdminPass123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        List<String> setCookies = login.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anyMatch(c -> c.startsWith("df_access=") && c.contains("HttpOnly"));
        assertThat(setCookies).anyMatch(c -> c.startsWith("df_refresh=")
                && c.contains("Path=/api/v1/auth")
                && c.contains("HttpOnly"));

        Cookie access = cookieFrom(setCookies, "df_access");
        mockMvc.perform(get("/api/v1/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("admin@cookie-co.test"));
    }

    @Test
    void bearerWinsOverConflictingCookie() throws Exception {
        JsonNode adminTokens = login("admin@cookie-co.test", "AdminPass123!");
        MvcResult viewerLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"cookie-co","email":"viewer@cookie-co.test","password":"ViewerPass123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Cookie viewerAccess = cookieFrom(viewerLogin.getResponse().getHeaders(HttpHeaders.SET_COOKIE), "df_access");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminTokens.get("accessToken").asText())
                        .cookie(viewerAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("admin@cookie-co.test"));
    }

    @Test
    void logoutClearsCookiesAndRefreshByCookieWorks() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"cookie-co","email":"admin@cookie-co.test","password":"AdminPass123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        List<String> setCookies = login.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        Cookie access = cookieFrom(setCookies, "df_access");
        Cookie refresh = cookieFrom(setCookies, "df_refresh");

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();
        Cookie newAccess = cookieFrom(refreshed.getResponse().getHeaders(HttpHeaders.SET_COOKIE), "df_access");
        Cookie newRefresh = cookieFrom(refreshed.getResponse().getHeaders(HttpHeaders.SET_COOKIE), "df_refresh");

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(newAccess, newRefresh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> cleared = logout.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cleared).anyMatch(c -> c.startsWith("df_access=") && c.contains("Max-Age=0"));
        assertThat(cleared).anyMatch(c -> c.startsWith("df_refresh=") && c.contains("Max-Age=0"));

        // Refresh token is revoked; cookie-only refresh must fail. Access JWT remains valid until expiry.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .cookie(newRefresh))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"cookie-co","email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private void saveUser(Company company, String email, String password, RoleCode role) {
        UserAccount user = new UserAccount();
        user.setCompany(company);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName("First");
        user.setLastName("Last");
        user.setEnabled(true);
        user.getRoles().add(roleRepository.findByCode(role.name()).orElseThrow());
        userAccountRepository.save(user);
    }

    private static Cookie cookieFrom(List<String> setCookies, String name) {
        String header = setCookies.stream()
                .filter(c -> c.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing cookie " + name));
        String value = header.substring(name.length() + 1).split(";", 2)[0];
        return new Cookie(name, value);
    }
}
