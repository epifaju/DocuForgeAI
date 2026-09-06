package ai.docuforge.businesspack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.businesspack.checksum.PackChecksumValidator;
import ai.docuforge.businesspack.validation.PackValidationReport;
import ai.docuforge.businesspack.validation.PackValidationService;
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
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

/**
 * Pack export — PRD §§97–98, §152 (export + validate VALID).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PackExportApiTest {

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
        registry.add("docuforge.storage.root", () -> "target/test-storage-pack-export");
        registry.add("docuforge.packs.platform-version", () -> "0.1.0");
        registry.add("docuforge.pdf.enabled", () -> "false");
    }

    @TempDir
    Path tempDir;

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
    @Autowired private PackValidationService packValidationService;

    private String adminToken;
    private String viewerToken;
    private String otherAdminToken;

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

        Company companyA = saveCompany("Export Co A", "export-a");
        Company companyB = saveCompany("Export Co B", "export-b");
        saveUser(companyA, "admin@export-a.test", "AdminPass123!", RoleCode.ADMIN);
        saveUser(companyA, "viewer@export-a.test", "ViewerPass123!", RoleCode.VIEWER);
        saveUser(companyB, "admin@export-b.test", "AdminPass123!", RoleCode.ADMIN);

        adminToken = login("export-a", "admin@export-a.test", "AdminPass123!");
        viewerToken = login("export-a", "viewer@export-a.test", "ViewerPass123!");
        otherAdminToken = login("export-b", "admin@export-b.test", "AdminPass123!");
    }

    @Test
    void exportProducesValidDbpfZipWithRecalculatedChecksums() throws Exception {
        UUID packId = installDemoPack();

        MvcResult export = mockMvc.perform(get("/api/v1/admin/business-packs/" + packId + "/export")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString(".zip")))
                .andReturn();

        byte[] zipBytes = export.getResponse().getContentAsByteArray();
        assertThat(zipBytes.length).isGreaterThan(100);

        Path zipPath = tempDir.resolve("exported.zip");
        Files.write(zipPath, zipBytes);

        try (ZipFile zipFile = ZipFile.builder().setPath(zipPath).get()) {
            assertThat(zipFile.getEntry("manifest.json")).isNotNull();
            assertThat(zipFile.getEntry("templates/demo.docx")).isNotNull();
            assertThat(zipFile.getEntry("metadata/demo.json")).isNotNull();
            assertThat(zipFile.getEntry("prompts/demo.txt")).isNotNull();

            byte[] manifestBytes = zipFile.getInputStream(zipFile.getEntry("manifest.json")).readAllBytes();
            JsonNode manifest = objectMapper.readTree(manifestBytes);
            assertThat(manifest.path("id").asText()).isEqualTo("com.docuforge.pack.demo");
            assertThat(manifest.path("checksums").isObject()).isTrue();

            Map<String, String> checksums = objectMapper.convertValue(
                    manifest.path("checksums"),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class)
            );
            assertThat(checksums).containsKeys(
                    "templates/demo.docx",
                    "metadata/demo.json",
                    "prompts/demo.txt"
            );

            for (Map.Entry<String, String> entry : checksums.entrySet()) {
                byte[] content = zipFile.getInputStream(zipFile.getEntry(entry.getKey())).readAllBytes();
                assertThat(checksumValidator.matches(entry.getValue(), content)).isTrue();
                assertThat(entry.getValue()).startsWith("sha256:");
            }
        }

        PackValidationReport report = packValidationService.validate(zipPath);
        assertThat(report.valid())
                .as(() -> "issues=" + report.issues())
                .isTrue();

        assertThat(auditLogRepository.findAll().stream().map(a -> a.getAction()).toList())
                .contains(AuditActions.PACK_EXPORTED);

        mockMvc.perform(post("/api/v1/admin/business-packs/" + packId + "/export")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"));
    }

    @Test
    void exportForbiddenForViewerAndCrossTenant() throws Exception {
        UUID packId = installDemoPack();

        mockMvc.perform(get("/api/v1/admin/business-packs/" + packId + "/export")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/business-packs/" + packId + "/export")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherAdminToken)))
                .andExpect(status().isNotFound());
    }

    private UUID installDemoPack() throws Exception {
        MockMultipartFile zip = new MockMultipartFile(
                "file",
                "demo-pack.zip",
                "application/zip",
                buildValidPackZip()
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
                .andExpect(status().isOk());

        MvcResult install = mockMvc.perform(post("/api/v1/admin/business-packs/imports/" + jobId + "/install")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(install.getResponse().getContentAsString())
                .path("data").path("packId").asText());
    }

    private byte[] buildValidPackZip() throws Exception {
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
                  "description": "Export API fixture",
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
