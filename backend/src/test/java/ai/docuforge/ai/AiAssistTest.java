package ai.docuforge.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.domain.ai.AiRequestRepository;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
class AiAssistTest {

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
        registry.add("docuforge.storage.root", () -> "target/test-storage-ai");
        registry.add("docuforge.pdf.enabled", () -> "false");
        registry.add("docuforge.ai.enabled", () -> "true");
        registry.add("docuforge.ai.provider", () -> "stub");
        registry.add("docuforge.ai.ollama-model", () -> "stub-model");
    }

    @TestConfiguration
    static class StubAiConfig {
        @Bean
        @Primary
        AIProvider aiProvider() {
            return request -> new AIResponse(
                    "STUB:" + request.operation() + ":" + (request.text() == null ? "" : request.text()),
                    "stub",
                    "stub-model",
                    PromptCatalog.PROMPT_VERSION,
                    12L
            );
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private AiRequestRepository aiRequestRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private TemplateVersionRepository templateVersionRepository;
    @Autowired private TemplateVariableRepository templateVariableRepository;
    @Autowired private GeneratedDocumentRepository generatedDocumentRepository;

    private String adminToken;
    private UUID templateId;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        aiRequestRepository.deleteAll();
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

        Company company = new Company();
        company.setName("Ai Co");
        company.setIdentifier("ai-co");
        company = companyRepository.save(company);

        UserAccount admin = new UserAccount();
        admin.setCompany(company);
        admin.setEmail("admin@ai-co.test");
        admin.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
        admin.setFirstName("Ada");
        admin.setLastName("Admin");
        admin.setEnabled(true);
        admin.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN.name()).orElseThrow());
        userAccountRepository.save(admin);

        adminToken = login("ai-co", "admin@ai-co.test", "AdminPass123!");
        templateId = createActiveTemplate();
    }

    @Test
    void rewriteReturnsStubContent() throws Exception {
        mockMvc.perform(post("/api/v1/ai/rewrite")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"bonjour le monde"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("STUB:REWRITE:bonjour le monde"))
                .andExpect(jsonPath("$.data.operation").value("REWRITE"))
                .andExpect(jsonPath("$.data.provider").value("stub"));
    }

    @Test
    void generateStillWorksWhenAiEnabled() throws Exception {
        mockMvc.perform(post("/api/v1/documents/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "%s",
                                  "data": {
                                    "client.firstName": "Alice",
                                    "client.email": "a@example.com",
                                    "invoice.total": 10
                                  }
                                }
                                """.formatted(templateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GENERATED"));
    }

    @Test
    void statusShowsEnabled() throws Exception {
        mockMvc.perform(get("/api/v1/ai/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    private UUID createActiveTemplate() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ai_tpl","name":"AI template"}
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/templates/" + id + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        return id;
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
