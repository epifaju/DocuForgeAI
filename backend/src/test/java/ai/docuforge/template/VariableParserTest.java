package ai.docuforge.template;

import static org.assertj.core.api.Assertions.assertThat;
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
class VariableParserTest {

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
        registry.add("docuforge.storage.root", () -> "target/test-storage-variables");
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

        Company companyA = saveCompany("Var Co A", "var-a");
        Company companyB = saveCompany("Var Co B", "var-b");
        saveUser(companyA, "admin@var-a.test", "AdminPass123!", RoleCode.ADMIN);
        saveUser(companyA, "viewer@var-a.test", "ViewerPass123!", RoleCode.VIEWER);
        saveUser(companyB, "admin@var-b.test", "AdminPass123!", RoleCode.ADMIN);

        adminToken = login("var-a", "admin@var-a.test", "AdminPass123!");
        viewerToken = login("var-a", "viewer@var-a.test", "ViewerPass123!");
        otherAdminToken = login("var-b", "admin@var-b.test", "AdminPass123!");
    }

    @Test
    void uploadDetectsVariablesAndAllowsConfiguration() throws Exception {
        UUID templateId = createTemplate(adminToken, "letter", "Letter");
        byte[] docx = DocxTestFixtures.minimalDocx(
                "Bonjour {{client.firstName}},",
                "Reference {{case.reference}} email {{client.email}}"
        );

        MvcResult upload = mockMvc.perform(multipart("/api/v1/templates/" + templateId + "/versions")
                        .file(new MockMultipartFile("file", "letter.docx", DOCX_MIME, docx))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID versionId = UUID.fromString(
                objectMapper.readTree(upload.getResponse().getContentAsString())
                        .path("data").path("id").asText()
        );

        mockMvc.perform(get("/api/v1/template-versions/" + versionId + "/variables")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].key").value("client.firstName"))
                .andExpect(jsonPath("$.data[1].key").value("case.reference"))
                .andExpect(jsonPath("$.data[2].key").value("client.email"))
                .andExpect(jsonPath("$.data[2].type").value("EMAIL"));

        mockMvc.perform(get("/api/v1/template-versions/" + versionId + "/variables")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/template-versions/" + versionId + "/variables")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "variables": [
                                    {
                                      "key": "client.firstName",
                                      "label": "Prenom client",
                                      "type": "TEXT",
                                      "required": true,
                                      "displayOrder": 0,
                                      "placeholder": "Alice"
                                    },
                                    {
                                      "key": "case.reference",
                                      "label": "Reference dossier",
                                      "type": "TEXT",
                                      "required": true,
                                      "displayOrder": 1
                                    },
                                    {
                                      "key": "client.email",
                                      "label": "Email client",
                                      "type": "EMAIL",
                                      "required": false,
                                      "displayOrder": 2
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].label").value("Prenom client"))
                .andExpect(jsonPath("$.data[2].required").value(false));

        mockMvc.perform(put("/api/v1/template-versions/" + versionId + "/variables")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variables":[]}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/template-versions/" + versionId + "/variables")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherAdminToken)))
                .andExpect(status().isNotFound());

        assertThat(templateVariableRepository.countByTemplateVersionId(versionId)).isEqualTo(3);
    }

    @Test
    void corruptDocxIsRejected() throws Exception {
        UUID templateId = createTemplate(adminToken, "bad_docx", "Bad");
        byte[] bogus = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x00, 0x01};
        mockMvc.perform(multipart("/api/v1/templates/" + templateId + "/versions")
                        .file(new MockMultipartFile("file", "bad.docx", DOCX_MIME, bogus))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());
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
}