package ai.docuforge.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.domain.audit.AuditLogRepository;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.email.EmailDeliveryRepository;
import ai.docuforge.domain.email.EmailDeliveryStatus;
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
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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
class DocumentEmailTest {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @RegisterExtension
    static final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
            .withPerMethodLifecycle(false);

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
        registry.add("docuforge.storage.root", () -> "target/test-storage-email");
        registry.add("docuforge.pdf.enabled", () -> "false");
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
        registry.add("spring.mail.username", () -> "");
        registry.add("spring.mail.password", () -> "");
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("docuforge.mail.enabled", () -> "true");
        registry.add("docuforge.mail.from", () -> "noreply@docuforge.test");
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
    @Autowired private EmailDeliveryRepository emailDeliveryRepository;

    private String adminToken;
    private UUID templateId;

    @BeforeEach
    void setUp() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        emailDeliveryRepository.deleteAll();
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
        company.setName("Mail Co");
        company.setIdentifier("mail-co");
        company = companyRepository.save(company);

        UserAccount admin = new UserAccount();
        admin.setCompany(company);
        admin.setEmail("admin@mail-co.test");
        admin.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
        admin.setFirstName("Ada");
        admin.setLastName("Admin");
        admin.setEnabled(true);
        admin.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN.name()).orElseThrow());
        userAccountRepository.save(admin);

        adminToken = login("mail-co", "admin@mail-co.test", "AdminPass123!");
        templateId = createActiveTemplate("mail_tpl", "Mail template");
    }

    @Test
    void generateThenEmailDeliversDocxAttachment() throws Exception {
        UUID docId = generateDocument(templateId, "Contrat Mail", "Alice");
        String reference = generatedDocumentRepository.findById(docId).orElseThrow().getReference();

        mockMvc.perform(post("/api/v1/documents/" + docId + "/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipient": "client@example.com",
                                  "subject": "Votre contrat",
                                  "message": "Bonjour, voici le document.",
                                  "attachmentFormat": "DOCX",
                                  "confirmed": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.recipient").value("client@example.com"))
                .andExpect(jsonPath("$.data.attachmentFormat").value("DOCX"));

        assertThat(greenMail.waitForIncomingEmail(5_000, 1)).isTrue();
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        MimeMessage message = messages[0];
        assertThat(message.getAllRecipients()[0].toString()).contains("client@example.com");
        assertThat(message.getSubject()).isEqualTo("Votre contrat");
        assertThat(GreenMailUtil.getBody(message)).contains("Bonjour, voici le document.");

        MimeMultipart multipart = (MimeMultipart) message.getContent();
        boolean foundAttachment = false;
        for (int i = 0; i < multipart.getCount(); i++) {
            var part = multipart.getBodyPart(i);
            if (part.getFileName() != null && part.getFileName().equals(reference + ".docx")) {
                foundAttachment = true;
                byte[] bytes = part.getInputStream().readAllBytes();
                assertThat(bytes.length).isGreaterThan(100);
                assertThat(bytes[0]).isEqualTo((byte) 'P'); // ZIP/DOCX magic PK
                assertThat(bytes[1]).isEqualTo((byte) 'K');
            }
        }
        assertThat(foundAttachment).isTrue();

        assertThat(emailDeliveryRepository.findAll())
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
                    assertThat(d.getRecipient()).isEqualTo("client@example.com");
                });

        assertThat(auditLogRepository.findAll())
                .anyMatch(a -> "DOCUMENT_EMAILED".equals(a.getAction()));
    }

    @Test
    void emailRequiresConfirmation() throws Exception {
        UUID docId = generateDocument(templateId, "Sans confirm", "Bob");

        mockMvc.perform(post("/api/v1/documents/" + docId + "/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipient": "client@example.com",
                                  "subject": "Test",
                                  "message": "Hi",
                                  "attachmentFormat": "DOCX",
                                  "confirmed": false
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void emailPdfFailsWhenPdfMissing() throws Exception {
        UUID docId = generateDocument(templateId, "No PDF", "Carol");

        mockMvc.perform(post("/api/v1/documents/" + docId + "/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipient": "client@example.com",
                                  "subject": "PDF",
                                  "attachmentFormat": "PDF",
                                  "confirmed": true
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private UUID generateDocument(UUID tplId, String title, String firstName) throws Exception {
        MvcResult generated = mockMvc.perform(post("/api/v1/documents/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "%s",
                                  "title": "%s",
                                  "data": {
                                    "client.firstName": "%s",
                                    "client.email": "a@example.com",
                                    "invoice.total": 10
                                  }
                                }
                                """.formatted(tplId, title, firstName)))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(generated.getResponse().getContentAsString()).path("data").path("id").asText()
        );
    }

    private UUID createActiveTemplate(String code, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s"}
                                """.formatted(code, name)))
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
