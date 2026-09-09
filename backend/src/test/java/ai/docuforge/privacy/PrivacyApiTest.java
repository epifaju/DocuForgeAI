package ai.docuforge.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.domain.audit.AuditLogRepository;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.settings.ApplicationSettingRepository;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import ai.docuforge.settings.ApplicationSettingsService;
import ai.docuforge.settings.SettingKeys;
import ai.docuforge.template.DocxTestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PrivacyApiTest {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

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
        registry.add("docuforge.storage.root", () -> "target/test-storage-privacy");
        registry.add("docuforge.pdf.enabled", () -> "false");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private TemplateVersionRepository templateVersionRepository;
    @Autowired private TemplateVariableRepository templateVariableRepository;
    @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
    @Autowired private ApplicationSettingRepository applicationSettingRepository;
    @Autowired private ApplicationSettingsService applicationSettingsService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Company company;
    private String adminToken;
    private String editorToken;
    private String viewerToken;
    private String userToken;
    private UUID userId;
    private UUID templateId;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        generatedDocumentRepository.deleteAll();
        templateVariableRepository.deleteAll();
        templateRepository.findAll().forEach(t -> {
            t.setCurrentVersion(null);
            templateRepository.save(t);
        });
        templateVersionRepository.deleteAll();
        templateRepository.deleteAll();
        applicationSettingRepository.deleteAll();
        userAccountRepository.deleteAll();
        companyRepository.deleteAll();

        company = new Company();
        company.setName("Privacy Co");
        company.setIdentifier("privacy-co");
        company = companyRepository.save(company);

        saveUser("admin@privacy-co.test", "AdminPass123!", RoleCode.ADMIN);
        saveUser("editor@privacy-co.test", "EditorPass123!", RoleCode.EDITOR);
        saveUser("viewer@privacy-co.test", "ViewerPass123!", RoleCode.VIEWER);
        UserAccount user = saveUser("user@privacy-co.test", "UserPass123!", RoleCode.USER);
        userId = user.getId();

        adminToken = login("admin@privacy-co.test", "AdminPass123!");
        editorToken = login("editor@privacy-co.test", "EditorPass123!");
        viewerToken = login("viewer@privacy-co.test", "ViewerPass123!");
        userToken = login("user@privacy-co.test", "UserPass123!");
        templateId = createActiveTemplate(adminToken);
    }

    @Test
    void exportReturnsZipScopedToCurrentUser() throws Exception {
        generateDocument(userToken, "Export Doc");

        MvcResult export = mockMvc.perform(get("/api/v1/privacy/export")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("docuforge-data-export.zip")))
                .andExpect(content().contentTypeCompatibleWith("application/zip"))
                .andReturn();

        byte[] zipBytes = export.getResponse().getContentAsByteArray();
        assertThat(zipBytes.length).isGreaterThan(20);

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("export.json");
            byte[] json = zip.readAllBytes();
            JsonNode root = objectMapper.readTree(json);
            assertThat(root.path("user").path("email").asText()).isEqualTo("user@privacy-co.test");
            assertThat(root.path("documents").isArray()).isTrue();
            assertThat(root.path("documents")).hasSize(1);
            assertThat(root.path("documents").get(0).path("title").asText()).isEqualTo("Export Doc");
        }
    }

    @Test
    void deleteAccountAnonymizesUserRevokesTokensAndRemovesDocs() throws Exception {
        UUID docId = generateDocument(userToken, "To Delete");
        assertThat(generatedDocumentRepository.findById(docId)).isPresent();

        mockMvc.perform(delete("/api/v1/privacy/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk());

        UserAccount anonymized = userAccountRepository.findById(userId).orElseThrow();
        assertThat(anonymized.getEmail()).isEqualTo("deleted+" + userId + "@invalid.local");
        assertThat(anonymized.getFirstName()).isEqualTo("Deleted");
        assertThat(anonymized.getLastName()).isEqualTo("User");
        assertThat(anonymized.isEnabled()).isFalse();
        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_roles WHERE user_id = ?",
                Integer.class,
                userId
        );
        assertThat(roleCount).isZero();
        assertThat(generatedDocumentRepository.findById(docId)).isEmpty();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"privacy-co","email":"user@privacy-co.test","password":"UserPass123!"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void purgeIsAdminOnlyAndDeletesExpiredDocuments() throws Exception {
        applicationSettingsService.put(company.getId(), SettingKeys.DATA_RETENTION_DAYS, "30");

        UUID expiredId = generateDocument(adminToken, "Old Doc");
        jdbcTemplate.update(
                "UPDATE generated_documents SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(60, ChronoUnit.DAYS)),
                expiredId
        );

        UUID freshId = generateDocument(adminToken, "Fresh Doc");

        mockMvc.perform(post("/api/v1/privacy/purge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(editorToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/privacy/purge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/privacy/purge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(1));

        assertThat(generatedDocumentRepository.findById(expiredId)).isEmpty();
        assertThat(generatedDocumentRepository.findById(freshId)).isPresent();
    }

    private UUID generateDocument(String token, String title) throws Exception {
        MvcResult generated = mockMvc.perform(post("/api/v1/documents/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "%s",
                                  "title": "%s",
                                  "data": {
                                    "client.firstName": "Alice",
                                    "client.email": "alice@example.com",
                                    "invoice.total": 10
                                  }
                                }
                                """.formatted(templateId, title)))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(generated.getResponse().getContentAsString()).path("data").path("id").asText()
        );
    }

    private UserAccount saveUser(String email, String password, RoleCode role) {
        UserAccount account = new UserAccount();
        account.setCompany(company);
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setFirstName("First");
        account.setLastName("Last");
        account.setEnabled(true);
        account.getRoles().add(roleRepository.findByCode(role.name()).orElseThrow());
        return userAccountRepository.save(account);
    }

    private UUID createActiveTemplate(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"privacy_tpl","name":"Privacy template"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText()
        );
        byte[] docx = DocxTestFixtures.minimalDocx(
                "Hello {{client.firstName}}",
                "Mail {{client.email}} total {{invoice.total}}"
        );
        mockMvc.perform(multipart("/api/v1/templates/" + id + "/versions")
                        .file(new MockMultipartFile("file", "t.docx", DOCX_MIME, docx))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/templates/" + id + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        return id;
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyIdentifier":"privacy-co","email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
