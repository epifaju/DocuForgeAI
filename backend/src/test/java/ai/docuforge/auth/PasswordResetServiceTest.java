package ai.docuforge.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.domain.PasswordResetToken;
import ai.docuforge.auth.domain.PasswordResetTokenRepository;
import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.auth.dto.ForgotPasswordRequest;
import ai.docuforge.auth.dto.ResetPasswordRequest;
import ai.docuforge.config.DocuForgeProperties;
import ai.docuforge.config.MailProperties;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import ai.docuforge.settings.ApplicationSettingsService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JavaMailSender mailSender;
    @Mock private ApplicationSettingsService applicationSettingsService;
    @Mock private AuditService auditService;

    private PasswordResetService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                userAccountRepository,
                passwordResetTokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                mailSender,
                new MailProperties(false, "noreply@test", 1_000_000),
                applicationSettingsService,
                new DocuForgeProperties("local", "http://localhost:5174", null),
                auditService
        );
        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setIdentifier("authco");
        company.setName("Auth Co");
        user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setCompany(company);
        user.setEmail("admin@authco.test");
        user.setEnabled(true);
        user.setPasswordHash("old-hash");
        user.setFirstName("Ada");
        user.setLastName("Admin");
    }

    @Test
    void forgotPasswordSucceedsSilentlyForUnknownUser() {
        when(userAccountRepository.findByCompanyIdentifierAndEmail("authco", "missing@x.test"))
                .thenReturn(Optional.empty());

        assertThatCode(() -> service.forgotPassword(
                new ForgotPasswordRequest("authco", "missing@x.test"), "1.2.3.4"
        )).doesNotThrowAnyException();

        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailSender, never()).send(any(jakarta.mail.internet.MimeMessage.class));
    }

    @Test
    void forgotPasswordSucceedsSilentlyForDisabledUser() {
        user.setEnabled(false);
        when(userAccountRepository.findByCompanyIdentifierAndEmail("authco", user.getEmail()))
                .thenReturn(Optional.of(user));

        assertThatCode(() -> service.forgotPassword(
                new ForgotPasswordRequest("authco", user.getEmail()), "1.2.3.4"
        )).doesNotThrowAnyException();

        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void forgotPasswordCreatesTokenButSkipsMailWhenSmtpDisabled() {
        when(userAccountRepository.findByCompanyIdentifierAndEmail("authco", user.getEmail()))
                .thenReturn(Optional.of(user));

        service.forgotPassword(new ForgotPasswordRequest("authco", user.getEmail()), "1.2.3.4");

        verify(passwordResetTokenRepository).deleteByUserId(user.getId());
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).hasSize(64);
        verify(mailSender, never()).createMimeMessage();
        verify(auditService).recordForUser(
                eq(user),
                eq(AuditActions.PASSWORD_RESET_REQUESTED),
                eq("USER"),
                eq(user.getId().toString()),
                eq("SUCCESS"),
                eq("1.2.3.4"),
                eq(null)
        );
    }

    @Test
    void resetPasswordRejectsUnknownToken() {
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("bad.token", "NewPass123!")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash("hash");
        token.setExpiresAt(Instant.now().minusSeconds(10));
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("raw", "NewPass123!")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void resetPasswordUpdatesHashRevokesRefreshAndAudits() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(AuthService.hash("raw.token"));
        token.setExpiresAt(Instant.now().plusSeconds(600));
        when(passwordResetTokenRepository.findByTokenHash(AuthService.hash("raw.token")))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPass123!")).thenReturn("new-hash");

        service.resetPassword(new ResetPasswordRequest("raw.token", "NewPass123!"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.getUsedAt()).isNotNull();
        verify(userAccountRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
        verify(refreshTokenRepository).revokeAllActiveForUser(eq(user.getId()), any());
        verify(auditService).recordForUser(
                eq(user),
                eq(AuditActions.PASSWORD_RESET_COMPLETED),
                eq("USER"),
                eq(user.getId().toString()),
                eq("SUCCESS"),
                eq(null),
                eq(null)
        );
    }
}
