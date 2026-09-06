package ai.docuforge.businesspack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.businesspack.checksum.PackChecksumValidator;
import ai.docuforge.businesspack.demo.ArtisanDemoPackFactory;
import ai.docuforge.businesspack.validation.PackValidationReport;
import ai.docuforge.businesspack.validation.PackValidationService;
import ai.docuforge.domain.audit.AuditLogRepository;
import ai.docuforge.domain.businesspack.BusinessPackFileRepository;
import ai.docuforge.domain.businesspack.BusinessPackInstallationRepository;
import ai.docuforge.domain.businesspack.BusinessPackPromptRepository;
import ai.docuforge.domain.businesspack.BusinessPackRepository;
import ai.docuforge.domain.businesspack.BusinessPackStatus;
import ai.docuforge.domain.businesspack.BusinessPackTemplateRepository;
import ai.docuforge.domain.businesspack.BusinessPackType;
import ai.docuforge.domain.businesspack.BusinessPackVersionRepository;
import ai.docuforge.domain.businesspack.PackImportJobRepository;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateStatus;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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
 * Official artisan demo pack — PRD §§161–164 / Phase 21.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ArtisanDemoPackApiTest {

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
        registry.add("docuforge.storage.root", () -> "target/test-storage-artisan-demo");
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
    private Company company;

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

        company = saveCompany("Artisan Demo Co", "artisan-demo-co");
        saveUser(company, "admin@artisan-demo.test", "AdminPass123!", RoleCode.ADMIN);
        adminToken = login("artisan-demo-co", "admin@artisan-demo.test", "AdminPass123!");
    }

    @Test
    void artisanDemoPackValidatesAndInstallsWithThreeTemplates() throws Exception {
        byte[] zipBytes = ArtisanDemoPackFactory.buildZip(checksumValidator);
        Path zipPath = tempDir.resolve(ArtisanDemoPackFactory.RECOMMENDED_FILENAME);
        Files.write(zipPath, zipBytes);

        PackValidationReport report = packValidationService.validate(zipPath);
        assertThat(report.valid()).as(() -> "issues=" + report.issues()).isTrue();
        assertThat(report.pack().id()).isEqualTo(ArtisanDemoPackFactory.PACK_ID);
        assertThat(report.pack().version()).isEqualTo(ArtisanDemoPackFactory.PACK_VERSION);
        assertThat(report.summary().templates()).isEqualTo(3);
        assertThat(report.summary().prompts()).isEqualTo(2);

        MockMultipartFile zip = new MockMultipartFile(
                "file",
                ArtisanDemoPackFactory.RECOMMENDED_FILENAME,
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
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.detectedPackKey").value(ArtisanDemoPackFactory.PACK_ID));

        MvcResult install = mockMvc.perform(post("/api/v1/admin/business-packs/imports/" + jobId + "/install")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packKey").value(ArtisanDemoPackFactory.PACK_ID))
                .andExpect(jsonPath("$.data.version").value(ArtisanDemoPackFactory.PACK_VERSION))
                .andExpect(jsonPath("$.data.templatesInstalled").value(3))
                .andExpect(jsonPath("$.data.promptsInstalled").value(2))
                .andReturn();

        UUID packId = UUID.fromString(objectMapper.readTree(install.getResponse().getContentAsString())
                .path("data").path("packId").asText());

        var pack = businessPackRepository.findById(packId).orElseThrow();
        assertThat(pack.getPackKey()).isEqualTo(ArtisanDemoPackFactory.PACK_ID);
        assertThat(pack.getSlug()).isEqualTo(ArtisanDemoPackFactory.PACK_SLUG);
        assertThat(pack.getPackType()).isEqualTo(BusinessPackType.OFFICIAL);
        assertThat(pack.getStatus()).isEqualTo(BusinessPackStatus.INSTALLED);

        assertThat(templateRepository.findByCompanyIdAndCode(company.getId(), "ARTISAN_DEVIS"))
                .isPresent()
                .get()
                .extracting(t -> t.getStatus())
                .isEqualTo(TemplateStatus.ACTIVE);
        assertThat(templateRepository.findByCompanyIdAndCode(company.getId(), "ARTISAN_INTERVENTION")).isPresent();
        assertThat(templateRepository.findByCompanyIdAndCode(company.getId(), "ARTISAN_COMPLETION_CERTIFICATE"))
                .isPresent();

        var devis = templateRepository.findByCompanyIdAndCode(company.getId(), "ARTISAN_DEVIS").orElseThrow();
        mockMvc.perform(post("/api/v1/documents/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "%s",
                                  "title": "Devis Demo",
                                  "data": {
                                    "company.name": "Atelier Demo SARL",
                                    "company.address": "12 rue Fictive",
                                    "client.name": "Client Demo Martin",
                                    "client.address": "5 avenue Exemple",
                                    "quote.reference": "DEV-DEMO-001",
                                    "quote.date": "2026-09-01",
                                    "quote.validUntil": "2026-09-30",
                                    "work.description": "Travaux fictifs de demonstration",
                                    "pricing.subtotal": "100.00",
                                    "pricing.vat": "20.00",
                                    "pricing.total": "120.00"
                                  }
                                }
                                """.formatted(devis.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    private Company saveCompany(String name, String identifier) {
        Company entity = new Company();
        entity.setName(name);
        entity.setIdentifier(identifier);
        return companyRepository.save(entity);
    }

    private void saveUser(Company companyEntity, String email, String password, RoleCode role) {
        UserAccount user = new UserAccount();
        user.setCompany(companyEntity);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName("Test");
        user.setLastName(role.name());
        user.setEnabled(true);
        user.getRoles().add(roleRepository.findByCode(role.name()).orElseThrow());
        userAccountRepository.save(user);
    }

    private String login(String companyId, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyIdentifier": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(companyId, email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
