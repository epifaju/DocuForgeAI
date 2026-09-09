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
import ai.docuforge.auth.domain.RefreshToken;
import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.auth.dto.LoginRequest;
import ai.docuforge.auth.security.JwtService;
import ai.docuforge.config.JwtProperties;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.user.Role;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuditService auditService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private AuthService authService;
    private UserAccount user;
    private Company company;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userAccountRepository,
                refreshTokenRepository,
                auditService,
                passwordEncoder,
                jwtService,
                new JwtProperties("test-secret-key-with-at-least-32-characters!!", 900, 604_800)
        );
        company = new Company();
        company.setId(UUID.randomUUID());
        company.setIdentifier("authco");
        company.setName("Auth Co");
        user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setCompany(company);
        user.setEmail("admin@authco.test");
        user.setPasswordHash("hash");
        user.setEnabled(true);
        user.setFirstName("Ada");
        user.setLastName("Admin");
        Role admin = new Role();
        admin.setCode("ADMIN");
        user.getRoles().add(admin);
    }

    @Test
    void loginDisabledUserWritesFailureAudit() {
        user.setEnabled(false);
        when(userAccountRepository.findByCompanyIdentifierAndEmail("authco", user.getEmail()))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("authco", user.getEmail(), "pw"), "9.9.9.9"
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(auditService).recordForUser(
                eq(user), eq(AuditActions.LOGIN), eq("USER"), eq(user.getId().toString()),
                eq("FAILURE"), eq("9.9.9.9"), eq(null)
        );
        verify(jwtService, never()).createAccessToken(any());
    }

    @Test
    void refreshRejectsExpiredOrRevokedToken() {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(AuthService.hash("raw"));
        token.setExpiresAt(Instant.now().minusSeconds(5));
        when(refreshTokenRepository.findByTokenHash(AuthService.hash("raw"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh("raw"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshRejectsWhenUserDisabled() {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(AuthService.hash("raw"));
        token.setExpiresAt(Instant.now().plusSeconds(600));
        user.setEnabled(false);
        when(refreshTokenRepository.findByTokenHash(AuthService.hash("raw"))).thenReturn(Optional.of(token));
        when(userAccountRepository.findByIdWithCompanyAndRoles(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh("raw"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void logoutIgnoresUnknownTokenAndOtherUsersToken() {
        assertThatCode(() -> authService.logout(user.getId(), "missing")).doesNotThrowAnyException();
        verify(refreshTokenRepository, never()).save(any());

        UUID other = UUID.randomUUID();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(AuthService.hash("raw"));
        when(refreshTokenRepository.findByTokenHash(AuthService.hash("raw"))).thenReturn(Optional.of(token));

        authService.logout(other, "raw");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void meNotFoundReturns404() {
        UUID id = UUID.randomUUID();
        when(userAccountRepository.findByIdWithCompanyAndRoles(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.me(id))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void hashIsDeterministic() {
        assertThat(AuthService.hash("same")).isEqualTo(AuthService.hash("same"));
        assertThat(AuthService.hash("same")).isNotEqualTo(AuthService.hash("other"));
        assertThat(AuthService.hash("same")).hasSize(64);
    }
}
