package ai.docuforge.businesspack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.domain.audit.AuditLogRepository;
import ai.docuforge.domain.businesspack.PackImportJobRepository;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.Deflater;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
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

/**
 * Phase 22 security campaign: ZIP Slip, MIME spoof, path traversal on filename,
 * RBAC, tenant isolation, oversized upload (PRD §§141–142, Phase 22).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PackSecurityHardeningApiTest {

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
        registry.add("docuforge.storage.root", () -> "target/test-storage-pack-security");
        registry.add("docuforge.packs.platform-version", () -> "0.1.0");
        registry.add("docuforge.packs.max-upload-size-mb", () -> "1");
        registry.add("spring.servlet.multipart.max-file-size", () -> "2MB");
        registry.add("spring.servlet.multipart.max-request-size", () -> "2MB");
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

    private String adminToken;
    private String viewerToken;
    private String otherAdminToken;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        packImportJobRepository.deleteAll();
        userAccountRepository.deleteAll();
        companyRepository.deleteAll();

        Company companyA = saveCompany("Sec Co A", "sec-a");
        Company companyB = saveCompany("Sec Co B", "sec-b");
        saveUser(companyA, "admin@sec-a.test", "AdminPass123!", RoleCode.ADMIN);
        saveUser(companyA, "viewer@sec-a.test", "ViewerPass123!", RoleCode.VIEWER);
        saveUser(companyB, "admin@sec-b.test", "AdminPass123!", RoleCode.ADMIN);

        adminToken = login("sec-a", "admin@sec-a.test", "AdminPass123!");
        viewerToken = login("sec-a", "viewer@sec-a.test", "ViewerPass123!");
        otherAdminToken = login("sec-b", "admin@sec-b.test", "AdminPass123!");
    }

    @Test
    void zipSlipFailsValidationWithUnsafePath() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(bos)) {
            put(out, "templates/../../evil.txt", "pwned".getBytes(StandardCharsets.UTF_8));
        }
        MockMultipartFile zip = new MockMultipartFile(
                "file", "slip.zip", "application/zip", bos.toByteArray());

        MvcResult upload = mockMvc.perform(multipart("/api/v1/admin/business-packs/import")
                        .file(zip)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID jobId = UUID.fromString(objectMapper.readTree(upload.getResponse().getContentAsString())
                .path("data").path("jobId").asText());

        MvcResult validated = mockMvc.perform(post("/api/v1/admin/business-packs/imports/" + jobId + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INVALID"))
                .andReturn();

        JsonNode issues = objectMapper.readTree(validated.getResponse().getContentAsString())
                .path("data").path("validationReport").path("issues");
        assertThat(issues.isArray()).isTrue();
        assertThat(issues.toString()).contains("PACK_UNSAFE_PATH");
    }

    @Test
    void mimeSpoofRejectedAtUpload() throws Exception {
        MockMultipartFile spoof = new MockMultipartFile(
                "file",
                "evil.zip",
                "application/zip",
                "definitely-not-a-zip".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/admin/business-packs/import")
                        .file(spoof)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_ERROR"));

        assertThat(packImportJobRepository.count()).isZero();
    }

    @Test
    void nonZipExtensionRejected() throws Exception {
        MockMultipartFile exe = new MockMultipartFile(
                "file",
                "pack.exe",
                "application/octet-stream",
                new byte[] {0x50, 0x4B, 0x03, 0x04}
        );

        mockMvc.perform(multipart("/api/v1/admin/business-packs/import")
                        .file(exe)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_ERROR"));
    }

    @Test
    void pathTraversalInOriginalFilenameRejected() throws Exception {
        MockMultipartFile traversal = new MockMultipartFile(
                "file",
                "../../evil.zip",
                "application/zip",
                new byte[] {0x50, 0x4B, 0x03, 0x04, 0x00}
        );

        mockMvc.perform(multipart("/api/v1/admin/business-packs/import")
                        .file(traversal)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PATH_TRAVERSAL"));
    }

    @Test
    void viewerForbiddenAndCrossTenantNotFound() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(bos)) {
            put(out, "manifest.json", "{}".getBytes(StandardCharsets.UTF_8));
        }
        MockMultipartFile zip = new MockMultipartFile(
                "file", "tiny.zip", "application/zip", bos.toByteArray());

        mockMvc.perform(multipart("/api/v1/admin/business-packs/import")
                        .file(zip)
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/business-packs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isForbidden());

        MvcResult upload = mockMvc.perform(multipart("/api/v1/admin/business-packs/import")
                        .file(zip)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID jobId = UUID.fromString(objectMapper.readTree(upload.getResponse().getContentAsString())
                .path("data").path("jobId").asText());

        mockMvc.perform(get("/api/v1/admin/business-packs/imports/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherAdminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void oversizedUploadRejected() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] chunk = new byte[300_000];
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(bos)) {
            out.setLevel(Deflater.NO_COMPRESSION);
            for (int i = 0; i < 4; i++) {
                put(out, "part-" + i + ".txt", chunk);
            }
        }
        byte[] payload = bos.toByteArray();
        assertThat(payload.length).isGreaterThan(1 * 1024 * 1024);

        MockMultipartFile zip = new MockMultipartFile(
                "file", "huge.zip", "application/zip", payload);

        mockMvc.perform(multipart("/api/v1/admin/business-packs/import")
                        .file(zip)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isPayloadTooLarge());

        assertThat(packImportJobRepository.count()).isZero();
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
