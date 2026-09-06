package ai.docuforge.businesspack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.businesspack.checksum.PackChecksumValidator;
import ai.docuforge.domain.audit.AuditLogRepository;
import ai.docuforge.domain.businesspack.BusinessPackFileRepository;
import ai.docuforge.domain.businesspack.BusinessPackInstallationRepository;
import ai.docuforge.domain.businesspack.BusinessPackPromptRepository;
import ai.docuforge.domain.businesspack.BusinessPackRepository;
import ai.docuforge.domain.businesspack.BusinessPackTemplateRepository;
import ai.docuforge.domain.businesspack.BusinessPackVersionRepository;
import ai.docuforge.domain.businesspack.PackImportJobRepository;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.template.TemplateOrigin;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateStatus;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import ai.docuforge.storage.StorageProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
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
class PackTemplateDuplicateApiTest {

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
        registry.add("docuforge.storage.root", () -> "target/test-storage-pack-duplicate");
        registry.add("docuforge.packs.platform-version", () -> "0.1.0");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PackImportJobRepository packImportJobRepository;
    @Autowired private BusinessPackRepository businessPackRepository;
    @Autowired private BusinessPackVersionRepository businessPackVersionRepository;
    @Autowired private BusinessPackTemplateRepository businessPackTemplateRepository;
    @Autowired private BusinessPackPromptRepository businessPackPromptRepository;
    @Autowired private BusinessPackFileRepository businessPackFileRepository;
    @Autowired private BusinessPackInstallationRepository installationRepository;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private TemplateVersionRepository templateVersionRepository;
    @Autowired private TemplateVariableRepository templateVariableRepository;
    @Autowired private PackChecksumValidator checksumValidator;
    @Autowired private StorageProvider storageProvider;

    private String adminToken;
    private Company companyA;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        packImportJobRepository.deleteAll();
        businessPackTemplateRepository.deleteAll();
        businessPackFileRepository.deleteAll();
        businessPackPromptRepository.deleteAll();
        installationRepository.deleteAll();

        var templates = templateRepository.findAll();
        templates.forEach(t -> t.setCurrentVersion(null));
        templateRepository.saveAll(templates);
        templateRepository.flush();
        templateVariableRepository.deleteAll();
        templateVersionRepository.deleteAll();
        templateRepository.deleteAll();

        var packs = businessPackRepository.findAll();
        packs.forEach(p -> p.setCurrentVersion(null));
        businessPackRepository.saveAll(packs);
        businessPackRepository.flush();
        businessPackVersionRepository.deleteAll();
        businessPackRepository.deleteAll();

        userAccountRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = saveCompany("Dup Co A", "dup-a");
        saveUser(companyA, "admin@dup-a.test", "AdminPass123!", RoleCode.ADMIN);
        adminToken = login("dup-a", "admin@dup-a.test", "AdminPass123!");
    }

    @Test
    void duplicatePackTemplateCreatesIndependentUserCopy() throws Exception {
        UUID jobId = uploadAndValidate(buildPackZip());
        mockMvc.perform(post("/api/v1/admin/business-packs/imports/" + jobId + "/install")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enablePack":true,"enableTemplates":true}
                                """))
                .andExpect(status().isOk());

        var packTemplate = templateRepository.findByCompanyIdAndCode(companyA.getId(), "DEMO_QUOTE").orElseThrow();
        assertThat(packTemplate.getOrigin()).isEqualTo(TemplateOrigin.PACK);
        UUID packTemplateId = packTemplate.getId();

        mockMvc.perform(put("/api/v1/templates/" + packTemplateId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"DEMO_QUOTE","name":"Hack"}
                                """))
                .andExpect(status().isConflict());

        MvcResult dup = mockMvc.perform(post("/api/v1/templates/" + packTemplateId + "/duplicate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mon devis personnalise"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Mon devis personnalise"))
                .andExpect(jsonPath("$.data.code").value("DEMO_QUOTE_COPY"))
                .andExpect(jsonPath("$.data.origin").value("USER"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        UUID copyId = UUID.fromString(objectMapper.readTree(dup.getResponse().getContentAsString())
                .path("data").path("id").asText());
        assertThat(objectMapper.readTree(dup.getResponse().getContentAsString()).path("data").path("sourcePackId").isNull())
                .isTrue();

        var packLoaded = templateRepository.findByIdAndCompanyIdWithCurrentVersion(packTemplateId, companyA.getId())
                .orElseThrow();
        UUID packVersionId = packLoaded.getCurrentVersion().getId();
        String packStorageKey = packLoaded.getCurrentVersion().getStorageKey();
        int packVarCount = templateVariableRepository
                .findByTemplateVersionIdOrderByDisplayOrderAsc(packVersionId).size();

        var copy = templateRepository.findByIdAndCompanyIdWithCurrentVersion(copyId, companyA.getId()).orElseThrow();
        assertThat(copy.getOrigin()).isEqualTo(TemplateOrigin.USER);
        assertThat(copy.getSourcePack()).isNull();
        assertThat(copy.getStatus()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(copy.getCurrentVersion()).isNotNull();
        assertThat(copy.getCurrentVersion().getId()).isNotEqualTo(packVersionId);
        assertThat(copy.getCurrentVersion().getSourcePackVersion()).isNull();
        assertThat(copy.getCurrentVersion().getStorageKey()).isNotEqualTo(packStorageKey);
        assertThat(storageProvider.exists(copy.getCurrentVersion().getStorageKey())).isTrue();
        assertThat(storageProvider.exists(packStorageKey)).isTrue();
        assertThat(templateVariableRepository
                .findByTemplateVersionIdOrderByDisplayOrderAsc(copy.getCurrentVersion().getId()))
                .hasSize(packVarCount);

        mockMvc.perform(put("/api/v1/templates/" + copyId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"DEMO_QUOTE_COPY",
                                  "name":"Devis custom edite",
                                  "description":"independant"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Devis custom edite"));

        var packAfter = templateRepository.findById(packTemplateId).orElseThrow();
        assertThat(packAfter.getName()).isEqualTo("Demo Quote");
        assertThat(packAfter.getOrigin()).isEqualTo(TemplateOrigin.PACK);

        assertThat(auditLogRepository.findAll().stream().map(a -> a.getAction()).toList())
                .contains(AuditActions.TEMPLATE_CREATED);
    }

    private UUID uploadAndValidate(byte[] zipBytes) throws Exception {
        MockMultipartFile zip = new MockMultipartFile(
                "file",
                "demo-pack.zip",
                "application/zip",
                zipBytes
        );
        MvcResult upload = mockMvc.perform(multipart("/api/v1/admin/business-packs/import")
                        .file(zip)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID jobId = UUID.fromString(objectMapper.readTree(upload.getResponse().getContentAsString())
                .path("data").path("jobId").asText());

        mockMvc.perform(post("/api/v1/admin/business-packs/imports/" + jobId + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALID"));
        return jobId;
    }

    private byte[] buildPackZip() throws Exception {
        byte[] docx = docxWithText("Hello {{client.name}}");
        String metadata = """
                {
                  "schemaVersion": "DBPF-TEMPLATE-1",
                  "code": "DEMO_QUOTE",
                  "name": "Demo Quote",
                  "version": "1.0.0",
                  "outputFormats": ["DOCX"],
                  "variables": [
                    {"key": "client.name", "label": "Client", "type": "TEXT", "required": true, "order": 1}
                  ]
                }
                """;
        byte[] metadataBytes = metadata.getBytes(StandardCharsets.UTF_8);
        byte[] promptBytes = "You are a helpful document assistant.".getBytes(StandardCharsets.UTF_8);
        String manifest = """
                {
                  "schemaVersion": "DBPF-1",
                  "id": "com.docuforge.pack.demo",
                  "name": "Demo Pack",
                  "slug": "demo",
                  "version": "1.0.0",
                  "type": "CUSTOM",
                  "description": "Duplicate fixture",
                  "publisher": { "id": "docuforge", "name": "DocuForge AI" },
                  "compatibility": { "minimumDocuForgeVersion": "0.1.0" },
                  "locales": ["fr-FR"],
                  "defaultLocale": "fr-FR",
                  "templates": [
                    {
                      "code": "DEMO_QUOTE",
                      "name": "Demo Quote",
                      "version": "1.0.0",
                      "templateFile": "templates/demo.docx",
                      "metadataFile": "metadata/demo.json"
                    }
                  ],
                  "prompts": [
                    {
                      "code": "DEMO_ASSIST",
                      "version": "1.0.0",
                      "file": "prompts/demo.txt"
                    }
                  ],
                  "checksums": {
                    "templates/demo.docx": "%s",
                    "metadata/demo.json": "%s",
                    "prompts/demo.txt": "%s"
                  }
                }
                """.formatted(
                checksumValidator.digestPrefixed(docx),
                checksumValidator.digestPrefixed(metadataBytes),
                checksumValidator.digestPrefixed(promptBytes)
        );

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(bos)) {
            putZip(out, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            putZip(out, "templates/demo.docx", docx);
            putZip(out, "metadata/demo.json", metadataBytes);
            putZip(out, "prompts/demo.txt", promptBytes);
        }
        return bos.toByteArray();
    }

    private static byte[] docxWithText(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    private static void putZip(ZipArchiveOutputStream out, String name, byte[] data) throws Exception {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        out.putArchiveEntry(entry);
        out.write(data);
        out.closeArchiveEntry();
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
                                {
                                  "companyIdentifier": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(company, email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
