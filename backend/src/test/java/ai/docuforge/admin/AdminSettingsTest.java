package ai.docuforge.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.domain.audit.AuditLogRepository;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.settings.ApplicationSettingRepository;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AdminSettingsTest {

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
        registry.add("docuforge.storage.root", () -> "target/test-storage-admin-settings");
        registry.add("docuforge.pdf.enabled", () -> "false");
        registry.add("docuforge.ai.enabled", () -> "true");
        registry.add("docuforge.mail.enabled", () -> "true");
        registry.add("docuforge.mail.from", () -> "platform@docuforge.test");
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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ApplicationSettingRepository applicationSettingRepository;

    private String adminToken;
    private String editorToken;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        applicationSettingRepository.deleteAll();
        userAccountRepository.deleteAll();
        companyRepository.deleteAll();

        Company company = saveCompany("Settings Co", "settings-co");
        saveUser(company, "admin@settings-co.test", "AdminPass123!", RoleCode.ADMIN);
        saveUser(company, "editor@settings-co.test", "EditorPass123!", RoleCode.EDITOR);

        adminToken = login("settings-co", "admin@settings-co.test", "AdminPass123!");
        editorToken = login("settings-co", "editor@settings-co.test", "EditorPass123!");
    }

    @Test
    void adminCanGetAndUpdateSettings_editorForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(editorToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.name").value("Settings Co"))
                .andExpect(jsonPath("$.data.company.identifier").value("settings-co"))
                .andExpect(jsonPath("$.data.ai.platformEnabled").value(true))
                .andExpect(jsonPath("$.data.ai.companyEnabled").value(true))
                .andExpect(jsonPath("$.data.email.fromAddress").value("platform@docuforge.test"))
                .andExpect(jsonPath("$.data.privacy.retentionDays").value(365));

        mockMvc.perform(put("/api/v1/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "company": { "name": "Settings SARL" },
                                  "ai": { "companyEnabled": false },
                                  "email": { "fromAddress": "noreply@settings-co.test" },
                                  "privacy": { "retentionDays": 180 }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.name").value("Settings SARL"))
                .andExpect(jsonPath("$.data.ai.companyEnabled").value(false))
                .andExpect(jsonPath("$.data.ai.effectivelyEnabled").value(false))
                .andExpect(jsonPath("$.data.email.fromAddress").value("noreply@settings-co.test"))
                .andExpect(jsonPath("$.data.privacy.retentionDays").value(180));

        mockMvc.perform(put("/api/v1/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(editorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "company": { "name": "Hack" },
                                  "ai": { "companyEnabled": true },
                                  "email": { "fromAddress": "x@y.z" },
                                  "privacy": { "retentionDays": 90 }
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private Company saveCompany(String name, String identifier) {
        Company company = new Company();
        company.setName(name);
        company.setIdentifier(identifier);
        return companyRepository.save(company);
    }

    private void saveUser(Company company, String email, String password, RoleCode role) {
        UserAccount user = new UserAccount();
        user.setCompany(company);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName("Test");
        user.setLastName(role.name());
        user.setEnabled(true);
        user.getRoles().add(roleRepository.findByCode(role.name()).orElseThrow());
        userAccountRepository.save(user);
    }

    private String login(String company, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"%s","email":"%s","password":"%s"}
                                """.formatted(company, email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return data.get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
