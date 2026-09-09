package ai.docuforge.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.auth.domain.PasswordResetToken;
import ai.docuforge.auth.domain.PasswordResetTokenRepository;
import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.auth.security.JwtService;
import ai.docuforge.domain.audit.AuditLogRepository;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class AuthSecurityTest {

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
        registry.add(
                "docuforge.jwt.secret",
                () -> "test-secret-key-with-at-least-32-characters!!"
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        userAccountRepository.deleteAll();
        companyRepository.deleteAll();

        Company company = new Company();
        company.setName("Auth Co");
        company.setIdentifier("authco");
        company = companyRepository.save(company);

        UserAccount admin = new UserAccount();
        admin.setCompany(company);
        admin.setEmail("admin@authco.test");
        admin.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
        admin.setFirstName("Ada");
        admin.setLastName("Admin");
        admin.setEnabled(true);
        admin.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN.name()).orElseThrow());
        userAccountRepository.save(admin);

        UserAccount viewer = new UserAccount();
        viewer.setCompany(company);
        viewer.setEmail("viewer@authco.test");
        viewer.setPasswordHash(passwordEncoder.encode("ViewerPass123!"));
        viewer.setFirstName("Vera");
        viewer.setLastName("Viewer");
        viewer.setEnabled(true);
        viewer.getRoles().add(roleRepository.findByCode(RoleCode.VIEWER.name()).orElseThrow());
        userAccountRepository.save(viewer);

        UserAccount disabled = new UserAccount();
        disabled.setCompany(company);
        disabled.setEmail("disabled@authco.test");
        disabled.setPasswordHash(passwordEncoder.encode("DisabledPass123!"));
        disabled.setFirstName("Dan");
        disabled.setLastName("Disabled");
        disabled.setEnabled(false);
        disabled.getRoles().add(roleRepository.findByCode(RoleCode.USER.name()).orElseThrow());
        userAccountRepository.save(disabled);
    }

    @Test
    void loginMeAndAdminAccessSucceedForAdmin() throws Exception {
        JsonNode tokens = login("authco", "admin@authco.test", "AdminPass123!");
        String access = tokens.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("admin@authco.test"))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"));

        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("ADMIN"));
    }

    @Test
    void unauthenticatedMeReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtReturns401() throws Exception {
        UserAccount admin = userAccountRepository
                .findByCompanyIdentifierAndEmail("authco", "admin@authco.test")
                .orElseThrow();
        DocuForgePrincipal principal = new DocuForgePrincipal(
                admin.getId(),
                admin.getCompany().getId(),
                "authco",
                admin.getEmail(),
                admin.getPasswordHash(),
                true,
                Set.of("ADMIN")
        );
        Instant now = Instant.now();
        String expired = jwtService.createAccessToken(principal, now.minusSeconds(3600), now.minusSeconds(10));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCannotAccessAdminEndpoint() throws Exception {
        JsonNode tokens = login("authco", "viewer@authco.test", "ViewerPass123!");
        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void refreshRotatesTokenAndLogoutRevokesRefresh() throws Exception {
        JsonNode tokens = login("authco", "admin@authco.test", "AdminPass123!");
        String refresh = tokens.get("refreshToken").asText();

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        JsonNode newTokens = objectMapper.readTree(refreshed.getResponse().getContentAsString()).get("data");
        String newRefresh = newTokens.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refresh);

        // old refresh must fail after rotation
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + newTokens.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(newRefresh)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(newRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"authco","email":"admin@authco.test","password":"nope"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"authco","email":"disabled@authco.test","password":"DisabledPass123!"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordAcceptsAndResetPasswordUpdatesCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"authco","email":"unknown@authco.test"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"authco","email":"admin@authco.test"}
                                """))
                .andExpect(status().isOk());

        assertThat(passwordResetTokenRepository.count()).isEqualTo(1);

        UserAccount admin = userAccountRepository
                .findByCompanyIdentifierAndEmail("authco", "admin@authco.test")
                .orElseThrow();
        String rawToken = "reset." + UUID.randomUUID();
        PasswordResetToken stored = new PasswordResetToken();
        stored.setUser(admin);
        stored.setTokenHash(AuthService.hash(rawToken));
        stored.setExpiresAt(Instant.now().plusSeconds(600));
        passwordResetTokenRepository.deleteAll();
        passwordResetTokenRepository.save(stored);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"BrandNewPass123!"}
                                """.formatted(rawToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"authco","email":"admin@authco.test","password":"AdminPass123!"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"authco","email":"admin@authco.test","password":"BrandNewPass123!"}
                                """))
                .andExpect(status().isOk());
    }

    private JsonNode login(String company, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"%s","email":"%s","password":"%s"}
                                """.formatted(company, email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}