package ai.docuforge.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.auth.domain.RefreshTokenRepository;
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
class DocumentGenerationTest {

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
        registry.add("docuforge.storage.root", () -> "target/test-storage-docs");
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

    private String adminToken;
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
        userAccountRepository.deleteAll();
        companyRepository.deleteAll();

        Company company = new Company();
        company.setName("Doc Co");
        company.setIdentifier("doc-co");
        company = companyRepository.save(company);

        UserAccount admin = new UserAccount();
        admin.setCompany(company);
        admin.setEmail("admin@doc-co.test");
        admin.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
        admin.setFirstName("Ada");
        admin.setLastName("Admin");
        admin.setEnabled(true);
        admin.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN.name()).orElseThrow());
        userAccountRepository.save(admin);

        adminToken = login("doc-co", "admin@doc-co.test", "AdminPass123!");
        templateId = createActiveTemplate();
    }

    @Test
    void generatePersistsDocxAndAllowsDownload() throws Exception {
        MvcResult generated = mockMvc.perform(post("/api/v1/documents/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "%s",
                                  "title": "Contrat Alice",
                                  "data": {
                                    "client.firstName": "Alice",
                                    "client.email": "alice@example.com",
                                    "invoice.total": 42.5
                                  }
                                }
                                """.formatted(templateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GENERATED"))
                .andExpect(jsonPath("$.data.reference").isNotEmpty())
                .andExpect(jsonPath("$.data.docxStorageKey").isNotEmpty())
                .andReturn();

        UUID docId = UUID.fromString(
                objectMapper.readTree(generated.getResponse().getContentAsString()).path("data").path("id").asText()
        );

        mockMvc.perform(get("/api/v1/documents/" + docId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Contrat Alice"));

        MvcResult download = mockMvc.perform(get("/api/v1/documents/" + docId + "/download/docx")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray().length).isGreaterThan(100);
        assertThat(download.getResponse().getContentType()).contains("wordprocessingml");
        assertThat(generatedDocumentRepository.count()).isEqualTo(1);
    }

    @Test
    void invalidDataReturns422AndCreatesNoDocument() throws Exception {
        mockMvc.perform(post("/api/v1/documents/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "%s",
                                  "data": {
                                    "client.firstName": "",
                                    "client.email": "bad",
                                    "invoice.total": -1
                                  }
                                }
                                """.formatted(templateId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_FORM_DATA"));

        assertThat(generatedDocumentRepository.count()).isZero();
    }

    @Test
    void previewReturnsDocxWithoutPersisting() throws Exception {
        MvcResult preview = mockMvc.perform(post("/api/v1/documents/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "%s",
                                  "data": {
                                    "client.firstName": "Bob",
                                    "client.email": "bob@example.com",
                                    "invoice.total": 10
                                  }
                                }
                                """.formatted(templateId)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(preview.getResponse().getContentAsByteArray().length).isGreaterThan(100);
        assertThat(generatedDocumentRepository.count()).isZero();
    }

    private UUID createActiveTemplate() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"gen_tpl","name":"Generation template"}
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