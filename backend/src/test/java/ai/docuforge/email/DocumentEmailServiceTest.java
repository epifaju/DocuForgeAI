package ai.docuforge.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.config.MailProperties;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.document.GeneratedDocument;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.email.AttachmentFormat;
import ai.docuforge.domain.email.EmailDelivery;
import ai.docuforge.domain.email.EmailDeliveryRepository;
import ai.docuforge.domain.email.EmailDeliveryStatus;
import ai.docuforge.email.dto.DocumentEmailRequest;
import ai.docuforge.email.dto.DocumentEmailResponse;
import ai.docuforge.settings.ApplicationSettingsService;
import ai.docuforge.storage.StorageProvider;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DocumentEmailServiceTest {

    @Mock private GeneratedDocumentRepository generatedDocumentRepository;
    @Mock private EmailDeliveryRepository emailDeliveryRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private AuditService auditService;
    @Mock private StorageProvider storageProvider;
    @Mock private JavaMailSender mailSender;
    @Mock private ApplicationSettingsService applicationSettingsService;
    @Mock private PlatformTransactionManager transactionManager;

    private DocuForgePrincipal principal;
    private GeneratedDocument document;
    private final UUID companyId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        principal = new DocuForgePrincipal(
                UUID.randomUUID(), companyId, "mail-co", "admin@mail-co.test", "h", true, Set.of("ADMIN")
        );
        document = new GeneratedDocument();
        document.setId(documentId);
        document.setReference("DOC-1");
        document.setDocxStorageKey("generated/" + UUID.randomUUID() + ".docx");
    }

    @Test
    void rejectsWhenSmtpDisabled() {
        DocumentEmailService service = service(new MailProperties(false, "from@test", 1_000_000));

        assertThatThrownBy(() -> service.send(principal, documentId, request("msg")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void rejectsWhenFromMissing() {
        when(applicationSettingsService.getEmailFrom(companyId)).thenReturn(Optional.empty());
        DocumentEmailService service = service(new MailProperties(true, "  ", 1_000_000));

        assertThatThrownBy(() -> service.send(principal, documentId, request("msg")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Expediteur");
    }

    @Test
    void prefersCompanyFromAddress() throws Exception {
        when(applicationSettingsService.getEmailFrom(companyId)).thenReturn(Optional.of("company@mail-co.test"));
        when(generatedDocumentRepository.findByIdAndCompanyId(documentId, companyId))
                .thenReturn(Optional.of(document));
        when(storageProvider.exists(document.getDocxStorageKey())).thenReturn(true);
        when(storageProvider.read(document.getDocxStorageKey()))
                .thenReturn(new ByteArrayInputStream("docx".getBytes()));
        Company company = new Company();
        company.setId(companyId);
        when(companyRepository.getReferenceById(companyId)).thenReturn(company);
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);

        DocumentEmailService service = service(new MailProperties(true, "fallback@test", 1_000_000));
        DocumentEmailResponse response = service.send(principal, documentId, request(null));

        assertThat(response.status()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(response.recipient()).isEqualTo("recipient@example.com");
        assertThat(mime.getFrom()[0].toString()).contains("company@mail-co.test");
        verify(auditService).recordSuccess(
                eq(principal), eq(AuditActions.DOCUMENT_EMAILED), eq("DOCUMENT"), eq(documentId), any()
        );
    }

    @Test
    void rejectsOversizedAttachment() throws Exception {
        when(applicationSettingsService.getEmailFrom(companyId)).thenReturn(Optional.of("from@test"));
        when(generatedDocumentRepository.findByIdAndCompanyId(documentId, companyId))
                .thenReturn(Optional.of(document));
        when(storageProvider.exists(document.getDocxStorageKey())).thenReturn(true);
        when(storageProvider.read(document.getDocxStorageKey()))
                .thenReturn(new ByteArrayInputStream(new byte[100]));

        DocumentEmailService service = service(new MailProperties(true, "from@test", 10));

        assertThatThrownBy(() -> service.send(principal, documentId, request("hi")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void rejectsMissingDocxAndUnknownDocument() {
        when(applicationSettingsService.getEmailFrom(companyId)).thenReturn(Optional.of("from@test"));
        when(generatedDocumentRepository.findByIdAndCompanyId(documentId, companyId))
                .thenReturn(Optional.empty());
        DocumentEmailService service = service(new MailProperties(true, "from@test", 1_000_000));

        assertThatThrownBy(() -> service.send(principal, documentId, request("hi")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        when(generatedDocumentRepository.findByIdAndCompanyId(documentId, companyId))
                .thenReturn(Optional.of(document));
        when(storageProvider.exists(document.getDocxStorageKey())).thenReturn(false);

        assertThatThrownBy(() -> service.send(principal, documentId, request("hi")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DOCX");
    }

    @Test
    void smtpFailureMarksDeliveryFailed() throws Exception {
        when(applicationSettingsService.getEmailFrom(companyId)).thenReturn(Optional.of("from@test"));
        when(generatedDocumentRepository.findByIdAndCompanyId(documentId, companyId))
                .thenReturn(Optional.of(document));
        when(storageProvider.exists(document.getDocxStorageKey())).thenReturn(true);
        when(storageProvider.read(document.getDocxStorageKey()))
                .thenReturn(new ByteArrayInputStream("docx".getBytes()));
        when(companyRepository.getReferenceById(companyId)).thenReturn(new Company());
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        DocumentEmailService service = service(new MailProperties(true, "from@test", 1_000_000));

        assertThatThrownBy(() -> service.send(principal, documentId, request("body")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);

        ArgumentCaptor<EmailDelivery> captor = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(emailDeliveryRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues().stream().anyMatch(d -> d.getStatus() == EmailDeliveryStatus.FAILED)).isTrue();
        assertThat(captor.getAllValues().getLast().getErrorMessage()).contains("smtp down");
    }

    private DocumentEmailService service(MailProperties properties) {
        return new DocumentEmailService(
                generatedDocumentRepository,
                emailDeliveryRepository,
                companyRepository,
                auditService,
                storageProvider,
                mailSender,
                properties,
                applicationSettingsService,
                transactionManager
        );
    }

    private static DocumentEmailRequest request(String message) {
        return new DocumentEmailRequest(
                " recipient@example.com ",
                " Subject ",
                message,
                AttachmentFormat.DOCX,
                true
        );
    }
}
