package ai.docuforge.auth;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.domain.PasswordResetToken;
import ai.docuforge.auth.domain.PasswordResetTokenRepository;
import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.auth.dto.ForgotPasswordRequest;
import ai.docuforge.auth.dto.ResetPasswordRequest;
import ai.docuforge.config.DocuForgeProperties;
import ai.docuforge.config.MailProperties;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import ai.docuforge.settings.ApplicationSettingsService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final long TOKEN_TTL_SECONDS = 3_600;

    private final UserAccountRepository userAccountRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final ApplicationSettingsService applicationSettingsService;
    private final DocuForgeProperties docuForgeProperties;
    private final AuditService auditService;

    public PasswordResetService(
            UserAccountRepository userAccountRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            MailProperties mailProperties,
            ApplicationSettingsService applicationSettingsService,
            DocuForgeProperties docuForgeProperties,
            AuditService auditService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.applicationSettingsService = applicationSettingsService;
        this.docuForgeProperties = docuForgeProperties;
        this.auditService = auditService;
    }

    /**
     * Always returns successfully to avoid account enumeration.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, String ipAddress) {
        userAccountRepository
                .findByCompanyIdentifierAndEmail(request.companyIdentifier(), request.email())
                .filter(UserAccount::isEnabled)
                .ifPresent(user -> {
                    passwordResetTokenRepository.deleteByUserId(user.getId());
                    String raw = UUID.randomUUID() + "." + UUID.randomUUID();
                    PasswordResetToken token = new PasswordResetToken();
                    token.setUser(user);
                    token.setTokenHash(AuthService.hash(raw));
                    token.setExpiresAt(Instant.now().plusSeconds(TOKEN_TTL_SECONDS));
                    passwordResetTokenRepository.save(token);
                    sendResetMail(user, raw);
                    auditService.recordForUser(
                            user,
                            AuditActions.PASSWORD_RESET_REQUESTED,
                            "USER",
                            user.getId().toString(),
                            "SUCCESS",
                            truncateIp(ipAddress),
                            null
                    );
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken stored = passwordResetTokenRepository
                .findByTokenHash(AuthService.hash(request.token()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.auth.reset_token_invalid"));
        Instant now = Instant.now();
        if (!stored.isUsable(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.auth.reset_token_invalid");
        }
        UserAccount user = stored.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userAccountRepository.save(user);
        stored.setUsedAt(now);
        passwordResetTokenRepository.save(stored);
        refreshTokenRepository.revokeAllActiveForUser(user.getId(), now);
        auditService.recordForUser(
                user,
                AuditActions.PASSWORD_RESET_COMPLETED,
                "USER",
                user.getId().toString(),
                "SUCCESS",
                null,
                null
        );
    }

    private void sendResetMail(UserAccount user, String rawToken) {
        if (!mailProperties.enabled()) {
            log.warn("Password reset requested but SMTP disabled user={}", user.getEmail());
            return;
        }
        String from = applicationSettingsService
                .getEmailFrom(user.getCompany().getId())
                .filter(s -> !s.isBlank())
                .orElse(mailProperties.from());
        String base = docuForgeProperties.appBaseUrl();
        if (base == null || base.isBlank()) {
            base = "http://localhost:5174";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String link = base + "/reset-password?token=" + rawToken;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(user.getEmail());
            helper.setSubject("DocuForge AI — reinitialisation du mot de passe");
            helper.setText(
                    "Bonjour,\n\n"
                            + "Pour reinitialiser votre mot de passe DocuForge AI, ouvrez ce lien (valide 1 h) :\n"
                            + link
                            + "\n\nSi vous n'etes pas a l'origine de cette demande, ignorez cet email.\n",
                    false
            );
            mailSender.send(message);
        } catch (MessagingException ex) {
            log.error("Failed to send password reset email to {}", user.getEmail(), ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "error.email.failed");
        }
    }

    private static String truncateIp(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }
        return ipAddress.length() <= 45 ? ipAddress : ipAddress.substring(0, 45);
    }
}
