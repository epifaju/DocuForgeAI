package ai.docuforge.businesspack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import ai.docuforge.domain.businesspack.BusinessPackStatus;
import ai.docuforge.domain.businesspack.BusinessPackTemplateRepository;
import ai.docuforge.domain.businesspack.BusinessPackVersionRepository;
import ai.docuforge.domain.businesspack.PackImportJobRepository;
import ai.docuforge.domain.businesspack.PackInstallationType;
import ai.docuforge.domain.businesspack.PackVersionStatus;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.document.DocumentStatus;
import ai.docuforge.domain.document.GeneratedDocument;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.domain.template.TemplateVersion;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
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
class PackUpdateApiTest {

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
        registry.add("docuforge.storage.root", () -> "target/test-storage-pack-update");
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
    @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
    @Autowired private PackChecksumValidator checksumValidator;

    private String adminToken;
    private Company companyA;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        generatedDocumentRepository.deleteAll();
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

        companyA = saveCompany("Update Co A", "update-a");
        saveUser(companyA, "admin@update-a.test", "AdminPass123!", RoleCode.ADMIN);
        adminToken = login("update-a", "admin@update-a.test", "AdminPass123!");
    }

    @Test
    void updatePreviewDiffInstallSupersedesAndPreservesHistoricalDocument() throws Exception {
        UUID installJob = uploadAndValidate(buildPackZip("1.0.0", true, false));
        MvcResult install = mockMvc.perform(post("/api/v1/admin/business-packs/imports/" + installJob + "/install")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enablePack":true,"enableTemplates":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.installationType").value("FRESH"))
                .andReturn();

        UUID packId = UUID.fromString(objectMapper.readTree(install.getResponse().getContentAsString())
                .path("data").path("packId").asText());

        Template template = templateRepository.findByCompanyIdAndCode(companyA.getId(), "DEMO_QUOTE").orElseThrow();
        UUID historicalVersionId = template.getCurrentVersion().getId();
        TemplateVersion historicalVersion = templateVersionRepository.findById(historicalVersionId).orElseThrow();
        String historicalStorageKey = historicalVersion.getStorageKey();

        GeneratedDocument historical = new GeneratedDocument();
        historical.setCompany(companyA);
        historical.setTemplate(template);
        historical.setTemplateVersion(historicalVersion);
        historical.setRootDocumentId(UUID.randomUUID());
        historical.setDocumentVersionNumber(1);
        historical.setReference("HIST-001");
        historical.setTitle("Historical quote");
        historical.setStatus(DocumentStatus.COMPLETED);
        historical.setDataSnapshot("{\"client.name\":\"Acme\"}");
        historical.setDocxStorageKey("documents/hist-001.docx");
        historical.setCreatedBy(userAccountRepository.findAll().getFirst().getId());
        historical = generatedDocumentRepository.save(historical);
        UUID historicalDocId = historical.getId();
        historical.setRootDocumentId(historicalDocId);
        generatedDocumentRepository.save(historical);

        UUID updateJob = uploadAndValidate(buildPackZip("1.1.0", true, true));

        assertThat(businessPackRepository.findById(packId).orElseThrow().getStatus())
                .isEqualTo(BusinessPackStatus.UPDATE_AVAILABLE);

        mockMvc.perform(get("/api/v1/admin/business-packs/imports/" + updateJob + "/update-preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updateCandidate").value(true))
                .andExpect(jsonPath("$.data.freshInstall").value(false))
                .andExpect(jsonPath("$.data.installedVersion").value("1.0.0"))
                .andExpect(jsonPath("$.data.candidateVersion").value("1.1.0"))
                .andExpect(jsonPath("$.data.updateKind").value("MINOR"))
                .andExpect(jsonPath("$.data.summary.templatesUpdated").value(1))
                .andExpect(jsonPath("$.data.summary.variablesAdded").value(1))
                .andExpect(jsonPath("$.data.summary.requiredVariablesAdded").value(1))
                .andExpect(jsonPath("$.data.breakingChanges[0].code").exists());

        mockMvc.perform(post("/api/v1/admin/business-packs/imports/" + updateJob + "/install")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enablePack":true,"enableTemplates":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.installationType").value("UPDATE"))
                .andExpect(jsonPath("$.data.version").value("1.1.0"));

        var pack = businessPackRepository.findByCompanyIdAndPackKeyWithCurrentVersion(companyA.getId(), "com.docuforge.pack.demo")
                .orElseThrow();
        assertThat(pack.getStatus()).isEqualTo(BusinessPackStatus.INSTALLED);
        assertThat(pack.getCurrentVersion().getVersion()).isEqualTo("1.1.0");

        var versions = businessPackVersionRepository.findByBusinessPackIdOrderByCreatedAtDesc(packId);
        assertThat(versions).hasSize(2);
        assertThat(versions.stream().filter(v -> "1.0.0".equals(v.getVersion())).findFirst().orElseThrow().getStatus())
                .isEqualTo(PackVersionStatus.SUPERSEDED);
        assertThat(versions.stream().filter(v -> "1.1.0".equals(v.getVersion())).findFirst().orElseThrow().getStatus())
                .isEqualTo(PackVersionStatus.INSTALLED);

        assertThat(installationRepository.findAll().stream()
                .anyMatch(i -> i.getInstallationType() == PackInstallationType.UPDATE)).isTrue();

        assertThat(auditLogRepository.findAll().stream().map(a -> a.getAction()).toList())
                .contains(AuditActions.PACK_UPDATED);

        GeneratedDocument after = generatedDocumentRepository.findById(historicalDocId).orElseThrow();
        assertThat(after.getTemplateVersion().getId()).isEqualTo(historicalVersionId);
        assertThat(after.getDocxStorageKey()).isEqualTo("documents/hist-001.docx");
        assertThat(after.getDataSnapshot()).contains("Acme");
        assertThat(objectMapper.readTree(after.getDataSnapshot()).path("client.name").asText()).isEqualTo("Acme");
        assertThat(after.getReference()).isEqualTo("HIST-001");
        assertThat(after.getRootDocumentId()).isEqualTo(historicalDocId);

        Template refreshed = templateRepository.findById(template.getId()).orElseThrow();
        UUID newCurrentVersionId = refreshed.getCurrentVersion().getId();
        assertThat(newCurrentVersionId).isNotEqualTo(historicalVersionId);
        assertThat(templateVersionRepository.findById(historicalVersionId).orElseThrow().getStorageKey())
                .isEqualTo(historicalStorageKey);
    }

    @Test
    void previewMarksFreshInstallWhenPackMissing() throws Exception {
        UUID jobId = uploadAndValidate(buildPackZip("1.0.0", false, false));
        mockMvc.perform(get("/api/v1/admin/business-packs/imports/" + jobId + "/update-preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updateCandidate").value(false))
                .andExpect(jsonPath("$.data.freshInstall").value(true))
                .andExpect(jsonPath("$.data.candidateVersion").value("1.0.0"));
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

    private byte[] buildPackZip(String packVersion, boolean includeNotesVar, boolean addEmailVar) throws Exception {
        byte[] docx = docxWithText(addEmailVar
                ? "Hello {{client.name}} {{client.email}}"
                : "Hello {{client.name}}");
        StringBuilder variables = new StringBuilder();
        variables.append("""
                {"key": "client.name", "label": "Client", "type": "TEXT", "required": true, "order": 1}
                """);
        if (includeNotesVar && !addEmailVar) {
            variables.append("""
                    ,{"key": "quote.notes", "label": "Notes", "type": "TEXT", "required": false, "order": 2}
                    """);
        }
        if (addEmailVar) {
            variables.append("""
                    ,{"key": "client.email", "label": "Email", "type": "EMAIL", "required": true, "order": 2}
                    """);
        }
        String templateVersion = "1.0.0".equals(packVersion) ? "1.0.0" : "1.1.0";
        String metadata = """
                {
                  "schemaVersion": "DBPF-TEMPLATE-1",
                  "code": "DEMO_QUOTE",
                  "name": "Demo Quote",
                  "version": "%s",
                  "outputFormats": ["DOCX"],
                  "variables": [
                    %s
                  ]
                }
                """.formatted(templateVersion, variables);
        byte[] metadataBytes = metadata.getBytes(StandardCharsets.UTF_8);
        String promptBody = addEmailVar
                ? "You are a helpful document assistant v2."
                : "You are a helpful document assistant.";
        byte[] promptBytes = promptBody.getBytes(StandardCharsets.UTF_8);
        String promptVersion = addEmailVar ? "1.0.1" : "1.0.0";
        String manifest = """
                {
                  "schemaVersion": "DBPF-1",
                  "id": "com.docuforge.pack.demo",
                  "name": "Demo Pack",
                  "slug": "demo",
                  "version": "%s",
                  "type": "CUSTOM",
                  "description": "Update API fixture",
                  "publisher": { "id": "docuforge", "name": "DocuForge AI" },
                  "compatibility": { "minimumDocuForgeVersion": "0.1.0" },
                  "locales": ["fr-FR"],
                  "defaultLocale": "fr-FR",
                  "templates": [
                    {
                      "code": "DEMO_QUOTE",
                      "name": "Demo Quote",
                      "version": "%s",
                      "templateFile": "templates/demo.docx",
                      "metadataFile": "metadata/demo.json"
                    }
                  ],
                  "prompts": [
                    {
                      "code": "DEMO_ASSIST",
                      "version": "%s",
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
                packVersion,
                templateVersion,
                promptVersion,
                checksumValidator.digestPrefixed(docx),
                checksumValidator.digestPrefixed(metadataBytes),
                checksumValidator.digestPrefixed(promptBytes)
        );

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(bos)) {
            put(out, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            put(out, "templates/demo.docx", docx);
            put(out, "metadata/demo.json", metadataBytes);
            put(out, "prompts/demo.txt", promptBytes);
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

    private static void put(ZipArchiveOutputStream out, String name, byte[] data) throws Exception {
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
