package ai.docuforge.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.domain.audit.AuditLogRepository;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
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
class TemplateManagementTest {

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
        registry.add(
                "docuforge.jwt.secret",
                () -> "test-secret-key-with-at-least-32-characters!!"
        );
        registry.add("docuforge.storage.root", () -> "target/test-storage-templates");
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
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private TemplateVersionRepository templateVersionRepository;

    @Autowired
    private TemplateVariableRepository templateVariableRepository;

    private Company companyA;
    private Company companyB;
    private String adminToken;
    private String viewerToken;
    private String otherAdminToken;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        templateVariableRepository.deleteAll();
        templateRepository.findAll().forEach(t -> {
            t.setCurrentVersion(null);
            templateRepository.save(t);
        });
        templateVersionRepository.deleteAll();
        templateRepository.deleteAll();
        userAccountRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = saveCompany("Template Co A", "tpl-a");
        companyB = saveCompany("Template Co B", "tpl-b");

        saveUser(companyA, "admin@tpl-a.test", "AdminPass123!", RoleCode.ADMIN);
        saveUser(companyA, "viewer@tpl-a.test", "ViewerPass123!", RoleCode.VIEWER);
        saveUser(companyB, "admin@tpl-b.test", "AdminPass123!", RoleCode.ADMIN);

        adminToken = login("tpl-a", "admin@tpl-a.test", "AdminPass123!");
        viewerToken = login("tpl-a", "viewer@tpl-a.test", "ViewerPass123!");
        otherAdminToken = login("tpl-b", "admin@tpl-b.test", "AdminPass123!");
    }

    @Test
    void crudUploadActivateArchiveAndIsolation() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "contrat_v1",
                                  "name": "Contrat standard",
                                  "description": "Modele contrat",
                                  "category": "legal"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.code").value("contrat_v1"))
                .andReturn();

        UUID templateId = UUID.fromString(
                objectMapper.readTree(createResult.getResponse().getContentAsString())
                        .path("data").path("id").asText()
        );

        mockMvc.perform(get("/api/v1/templates/" + templateId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Contrat standard"));

        mockMvc.perform(put("/api/v1/templates/" + templateId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "contrat_v1",
                                  "name": "Contrat standard mis a jour",
                                  "description": "Desc",
                                  "category": "legal"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Contrat standard mis a jour"));

        MockMultipartFile file = docxFile("contrat.docx");
        mockMvc.perform(multipart("/api/v1/templates/" + templateId + "/versions")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.versionNumber").value(1))
                .andExpect(jsonPath("$.data.storageKey").isNotEmpty())
                .andExpect(jsonPath("$.data.checksum").isNotEmpty());

        mockMvc.perform(get("/api/v1/templates/" + templateId + "/versions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/v1/templates/" + templateId + "/versions/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNumber").value(1));

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/templates/" + templateId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherAdminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("q", "contrat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        assertThat(auditLogRepository.findAll().stream()
                .map(a -> a.getAction())
                .toList())
                .contains("TEMPLATE_CREATED", "TEMPLATE_UPDATED", "TEMPLATE_ACTIVATED", "TEMPLATE_ARCHIVED");
    }

    @Test
    void draftCanBeDeletedButActiveCannot() throws Exception {
        UUID draftId = createTemplate(adminToken, "draft_del", "Draft delete");
        mockMvc.perform(multipart("/api/v1/templates/" + draftId + "/versions")
                        .file(docxFile("draft.docx"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/templates/" + draftId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/templates/" + draftId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());

        UUID activeId = createTemplate(adminToken, "active_keep", "Active keep");
        mockMvc.perform(multipart("/api/v1/templates/" + activeId + "/versions")
                        .file(docxFile("active.docx"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/templates/" + activeId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/templates/" + activeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void viewerCanReadButCannotWrite() throws Exception {
        UUID templateId = createTemplate(adminToken, "viewer_read", "Viewer readable");

        mockMvc.perform(get("/api/v1/templates/" + templateId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"forbidden","name":"Nope"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/templates/" + templateId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"viewer_read","name":"Hacked"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/v1/templates/" + templateId + "/versions")
                        .file(docxFile("nope.docx"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void activateWithoutVersionFails() throws Exception {
        UUID templateId = createTemplate(adminToken, "no_version", "Sans version");
        mockMvc.perform(post("/api/v1/templates/" + templateId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void duplicateCodeRejected() throws Exception {
        createTemplate(adminToken, "dup_code", "First");
        mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"dup_code","name":"Second"}
                                """))
                .andExpect(status().isConflict());
    }

    private UUID createTemplate(String token, String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s"}
                                """.formatted(code, name)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText()
        );
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

    private static MockMultipartFile docxFile(String filename) throws Exception {
        return new MockMultipartFile("file", filename, DOCX_MIME, DocxTestFixtures.minimalDocx("Template body"));
    }
}