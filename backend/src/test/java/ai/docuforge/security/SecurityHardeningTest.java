package ai.docuforge.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.auth.security.JwtService;
import ai.docuforge.domain.audit.AuditLogRepository;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import ai.docuforge.template.DocxTestFixtures;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
class SecurityHardeningTest {

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
        registry.add("docuforge.storage.root", () -> "target/test-storage-security");
        registry.add("docuforge.storage.max-upload-size-mb", () -> "1");
        registry.add("docuforge.pdf.enabled", () -> "false");
        registry.add("docuforge.antivirus.enabled", () -> "false");
        registry.add("docuforge.rate-limit.enabled", () -> "true");
        registry.add("docuforge.rate-limit.login-per-minute", () -> "3");
        registry.add("docuforge.rate-limit.ai-per-minute", () -> "20");
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
    @Autowired private JwtService jwtService;

    private String adminToken;
    private String viewerToken;
    private UUID templateId;
    private Company companyA;

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
        userAccountRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = new Company();
        companyA.setName("Sec Co A");
        companyA.setIdentifier("sec-a");
        companyA = companyRepository.save(companyA);

        UserAccount admin = new UserAccount();
        admin.setCompany(companyA);
        admin.setEmail("admin@sec-a.test");
        admin.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
        admin.setFirstName("Ada");
        admin.setLastName("Admin");
        admin.setEnabled(true);
        admin.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN.name()).orElseThrow());
        userAccountRepository.save(admin);

        UserAccount viewer = new UserAccount();
        viewer.setCompany(companyA);
        viewer.setEmail("viewer@sec-a.test");
        viewer.setPasswordHash(passwordEncoder.encode("ViewerPass123!"));
        viewer.setFirstName("Vic");
        viewer.setLastName("Viewer");
        viewer.setEnabled(true);
        viewer.getRoles().add(roleRepository.findByCode(RoleCode.VIEWER.name()).orElseThrow());
        userAccountRepository.save(viewer);

        String runKey = UUID.randomUUID().toString().substring(0, 8);
        adminToken = login("sec-a", "admin@sec-a.test", "AdminPass123!", "a-" + runKey);
        viewerToken = login("sec-a", "viewer@sec-a.test", "ViewerPass123!", "v-" + runKey);
        templateId = createActiveTemplate("sec_tpl", "Sec template");
    }

    @Test
    void unauthenticatedApiIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongRoleIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/documents/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "%s",
                                  "title": "Nope",
                                  "data": {
                                    "client.firstName": "X",
                                    "client.email": "x@example.com",
                                    "invoice.total": 1
                                  }
                                }
                                """.formatted(templateId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pathTraversalAndMaliciousFilenameRejected() throws Exception {
        byte[] docx = DocxTestFixtures.minimalDocx("Hello {{client.firstName}}");
        mockMvc.perform(multipart("/api/v1/templates/" + templateId + "/versions")
                        .file(new MockMultipartFile(
                                "file",
                                "../evil.docx",
                                DOCX_MIME,
                                docx
                        ))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidMimeRejectedAtStorage() throws Exception {
        mockMvc.perform(multipart("/api/v1/templates/" + templateId + "/versions")
                        .file(new MockMultipartFile(
                                "file",
                                "not-a-docx.txt",
                                "text/plain",
                                "hello".getBytes()
                        ))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedUploadRejected() throws Exception {
        byte[] hugeCsv = ("a,b\n" + "x,y\n".repeat(300_000)).getBytes();
        mockMvc.perform(multipart("/api/v1/batches")
                        .file(new MockMultipartFile("file", "big.csv", "text/csv", hugeCsv))
                        .param("templateId", templateId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void invalidAndExpiredJwtRejected() throws Exception {
        mockMvc.perform(get("/api/v1/documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        DocuForgePrincipal principal = new DocuForgePrincipal(
                UUID.randomUUID(),
                companyA.getId(),
                "sec-a",
                "admin@sec-a.test",
                "{noop}x",
                true,
                Set.of("ADMIN")
        );
        Instant now = Instant.now();
        String expired = jwtService.createAccessToken(principal, now.minusSeconds(3600), now.minusSeconds(60));
        mockMvc.perform(get("/api/v1/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crossCompanyDocumentAccessDenied() throws Exception {
        UUID docId = generateDocument(templateId, "Secret Doc", "Alice");

        Company companyB = new Company();
        companyB.setName("Sec Co B");
        companyB.setIdentifier("sec-b");
        companyB = companyRepository.save(companyB);

        UserAccount other = new UserAccount();
        other.setCompany(companyB);
        other.setEmail("admin@sec-b.test");
        other.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
        other.setFirstName("Bob");
        other.setLastName("Other");
        other.setEnabled(true);
        other.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN.name()).orElseThrow());
        userAccountRepository.save(other);

        String otherToken = login("sec-b", "admin@sec-b.test", "AdminPass123!", "b-" + UUID.randomUUID().toString().substring(0, 8));

        mockMvc.perform(get("/api/v1/documents/" + docId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/documents/" + docId + "/download/docx")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void loginRateLimitEventuallyBlocks() throws Exception {
        String body = """
                {"companyIdentifier":"sec-a","email":"admin@sec-a.test","password":"wrong-password"}
                """;
        String client = "rl-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", client)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    private UUID generateDocument(UUID tplId, String title, String firstName) throws Exception {
        MvcResult generated = mockMvc.perform(post("/api/v1/documents/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "%s",
                                  "title": "%s",
                                  "data": {
                                    "client.firstName": "%s",
                                    "client.email": "a@example.com",
                                    "invoice.total": 10
                                  }
                                }
                                """.formatted(tplId, title, firstName)))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(generated.getResponse().getContentAsString()).path("data").path("id").asText()
        );
    }

    private UUID createActiveTemplate(String code, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s"}
                                """.formatted(code, name)))
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/templates/" + id + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        return id;
    }

    private String login(String company, String email, String password, String clientKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", clientKey)
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
