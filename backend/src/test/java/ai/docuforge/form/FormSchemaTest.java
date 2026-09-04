package ai.docuforge.form;

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
class FormSchemaTest {

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
        registry.add("docuforge.storage.root", () -> "target/test-storage-forms");
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

        Company company = new Company();
        company.setName("Form Co");
        company.setIdentifier("form-co");
        company = companyRepository.save(company);

        UserAccount admin = new UserAccount();
        admin.setCompany(company);
        admin.setEmail("admin@form-co.test");
        admin.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
        admin.setFirstName("Ada");
        admin.setLastName("Admin");
        admin.setEnabled(true);
        admin.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN.name()).orElseThrow());
        userAccountRepository.save(admin);

        adminToken = login("form-co", "admin@form-co.test", "AdminPass123!");
    }

    @Test
    void formSchemaAndValidateFlow() throws Exception {
        UUID templateId = createTemplate();
        byte[] docx = DocxTestFixtures.minimalDocx(
                "Hello {{client.firstName}} {{client.email}} total {{invoice.total}}"
        );
        MvcResult upload = mockMvc.perform(multipart("/api/v1/templates/" + templateId + "/versions")
                        .file(new MockMultipartFile("file", "f.docx", DOCX_MIME, docx))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID versionId = UUID.fromString(
                objectMapper.readTree(upload.getResponse().getContentAsString()).path("data").path("id").asText()
        );

        mockMvc.perform(put("/api/v1/template-versions/" + versionId + "/variables")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "variables": [
                                    {"key":"client.firstName","label":"Prenom","type":"TEXT","required":true,"displayOrder":0,
                                     "configuration":"{\\"validation\\":{\\"minLength\\":2}}"},
                                    {"key":"client.email","label":"Email","type":"EMAIL","required":true,"displayOrder":1},
                                    {"key":"invoice.total","label":"Total","type":"CURRENCY","required":true,"displayOrder":2}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/template-versions/" + versionId + "/form-schema")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fields.length()").value(3))
                .andExpect(jsonPath("$.data.fields[0].component").value("text"))
                .andExpect(jsonPath("$.data.fields[1].component").value("email"))
                .andExpect(jsonPath("$.data.fields[2].component").value("number"))
                .andExpect(jsonPath("$.data.fields[0].validation.minLength").value(2));

        mockMvc.perform(post("/api/v1/template-versions/" + versionId + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":{"client.firstName":"A","client.email":"bad","invoice.total":-5}}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_FORM_DATA"))
                .andExpect(jsonPath("$.details").isArray());

        mockMvc.perform(post("/api/v1/template-versions/" + versionId + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":{
                                  "client.firstName":"Alice",
                                  "client.email":"alice@example.com",
                                  "invoice.total": 42.5
                                }}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
    }

    private UUID createTemplate() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"form_tpl","name":"Form template"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText()
        );
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