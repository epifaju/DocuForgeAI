package ai.docuforge.email;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.config.MailProperties;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.document.GeneratedDocument;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.email.AttachmentFormat;
import ai.docuforge.domain.email.EmailDelivery;
import ai.docuforge.domain.email.EmailDeliveryRepository;
import ai.docuforge.domain.email.EmailDeliveryStatus;
import ai.docuforge.email.dto.DocumentEmailRequest;
import ai.docuforge.email.dto.DocumentEmailResponse;
import ai.docuforge.storage.StorageProvider;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentEmailService {

    private static final Logger log = LoggerFactory.getLogger(DocumentEmailService.class);
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_MIME = "application/pdf";

    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final EmailDeliveryRepository emailDeliveryRepository;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;
    private final StorageProvider storageProvider;
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final TransactionTemplate requiresNewTx;

    public DocumentEmailService(
            GeneratedDocumentRepository generatedDocumentRepository,
            EmailDeliveryRepository emailDeliveryRepository,
            CompanyRepository companyRepository,
            AuditService auditService,
            StorageProvider storageProvider,
            JavaMailSender mailSender,
            MailProperties mailProperties,
            PlatformTransactionManager transactionManager
    ) {
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.emailDeliveryRepository = emailDeliveryRepository;
        this.companyRepository = companyRepository;
        this.auditService = auditService;
        this.storageProvider = storageProvider;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    public DocumentEmailResponse send(
            DocuForgePrincipal principal,
            UUID documentId,
            DocumentEmailRequest request
    ) {
        if (!mailProperties.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Envoi email desactive.");
        }
        if (mailProperties.from() == null || mailProperties.from().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Expediteur SMTP non configure.");
        }

        GeneratedDocument document = generatedDocumentRepository
                .findByIdAndCompanyId(documentId, principal.getCompanyId())
                .orElseThrow(() -> notFound("Document introuvable"));

        List<Attachment> attachments = resolveAttachments(document, request.attachmentFormat());
        long totalBytes = attachments.stream().mapToLong(a -> a.content().length).sum();
        if (totalBytes > mailProperties.maxAttachmentBytes()) {
            throw badRequest(
                    "Pieces jointes trop volumineuses (limite "
                            + mailProperties.maxAttachmentBytes()
                            + " octets)."
            );
        }

        EmailDelivery delivery = new EmailDelivery();
        delivery.setCompany(companyRepository.getReferenceById(principal.getCompanyId()));
        delivery.setDocument(document);
        delivery.setRecipient(request.recipient().trim());
        delivery.setSubject(request.subject().trim());
        delivery.setAttachmentFormat(request.attachmentFormat());
        delivery.setStatus(EmailDeliveryStatus.PENDING);
        delivery.setCreatedBy(principal.getUserId());
        persist(delivery);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailProperties.from());
            helper.setTo(delivery.getRecipient());
            helper.setSubject(delivery.getSubject());
            String body = request.message() == null || request.message().isBlank()
                    ? "Veuillez trouver ci-joint le document " + document.getReference() + "."
                    : request.message().trim();
            helper.setText(body, false);
            for (Attachment attachment : attachments) {
                helper.addAttachment(
                        attachment.filename(),
                        new ByteArrayResource(attachment.content()),
                        attachment.contentType()
                );
            }
            mailSender.send(message);

            delivery.setStatus(EmailDeliveryStatus.SENT);
            delivery.setSentAt(Instant.now());
            persist(delivery);
            writeAudit(principal, document.getId(), delivery.getRecipient());
            log.info(
                    "Email envoye documentId={} deliveryId={} recipient={} format={}",
                    document.getId(),
                    delivery.getId(),
                    delivery.getRecipient(),
                    delivery.getAttachmentFormat()
            );
            return toResponse(delivery);
        } catch (MailException | jakarta.mail.MessagingException ex) {
            delivery.setStatus(EmailDeliveryStatus.FAILED);
            delivery.setErrorMessage(truncate(ex.getMessage(), 2000));
            persist(delivery);
            log.warn(
                    "Echec envoi email documentId={} deliveryId={}: {}",
                    document.getId(),
                    delivery.getId(),
                    ex.getMessage()
            );
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Envoi email echoue.", ex);
        }
    }

    private List<Attachment> resolveAttachments(GeneratedDocument document, AttachmentFormat format) {
        List<Attachment> attachments = new ArrayList<>();
        if (format == AttachmentFormat.DOCX || format == AttachmentFormat.BOTH) {
            attachments.add(loadAttachment(
                    document.getDocxStorageKey(),
                    document.getReference() + ".docx",
                    DOCX_MIME,
                    "DOCX"
            ));
        }
        if (format == AttachmentFormat.PDF || format == AttachmentFormat.BOTH) {
            attachments.add(loadAttachment(
                    document.getPdfStorageKey(),
                    document.getReference() + ".pdf",
                    PDF_MIME,
                    "PDF"
            ));
        }
        return attachments;
    }

    private Attachment loadAttachment(String storageKey, String filename, String contentType, String label) {
        if (storageKey == null || storageKey.isBlank() || !storageProvider.exists(storageKey)) {
            throw badRequest("Fichier " + label + " indisponible pour ce document.");
        }
        try (InputStream in = storageProvider.read(storageKey)) {
            return new Attachment(filename, contentType, in.readAllBytes());
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Lecture piece jointe " + label + " impossible.",
                    ex
            );
        }
    }

    private void persist(EmailDelivery delivery) {
        requiresNewTx.executeWithoutResult(status -> emailDeliveryRepository.saveAndFlush(delivery));
    }

    private void writeAudit(DocuForgePrincipal principal, UUID documentId, String recipient) {
        auditService.recordSuccess(
                principal,
                AuditActions.DOCUMENT_EMAILED,
                "DOCUMENT",
                documentId,
                Map.of("recipient", recipient)
        );
    }

    private static String truncate(String message, int max) {
        if (message == null) {
            return null;
        }
        return message.length() <= max ? message : message.substring(0, max);
    }

    private static DocumentEmailResponse toResponse(EmailDelivery delivery) {
        return new DocumentEmailResponse(
                delivery.getId(),
                delivery.getDocument().getId(),
                delivery.getRecipient(),
                delivery.getSubject(),
                delivery.getAttachmentFormat(),
                delivery.getStatus(),
                delivery.getCreatedAt(),
                delivery.getSentAt()
        );
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record Attachment(String filename, String contentType, byte[] content) {
    }
}
