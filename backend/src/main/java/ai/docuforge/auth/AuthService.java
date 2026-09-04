package ai.docuforge.auth;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.domain.RefreshToken;
import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.auth.dto.LoginRequest;
import ai.docuforge.auth.dto.MeResponse;
import ai.docuforge.auth.dto.TokenResponse;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.auth.security.JwtService;
import ai.docuforge.config.JwtProperties;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserAccountRepository userAccountRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties
    ) {
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String ipAddress) {
        UserAccount user = userAccountRepository
                .findByCompanyIdentifierAndEmail(request.companyIdentifier(), request.email())
                .orElseThrow(() -> unauthorized("Identifiants invalides"));

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            writeAudit(user, AuditActions.LOGIN, "FAILURE", ipAddress);
            throw unauthorized("Identifiants invalides");
        }

        DocuForgePrincipal principal = toPrincipal(user);
        String accessToken = jwtService.createAccessToken(principal);
        String refreshToken = issueRefreshToken(user);
        writeAudit(user, AuditActions.LOGIN, "SUCCESS", ipAddress);
        return new TokenResponse(accessToken, refreshToken, "Bearer", jwtProperties.accessExpirationSeconds());
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> unauthorized("Refresh token invalide"));
        Instant now = Instant.now();
        if (!stored.isActive(now)) {
            throw unauthorized("Refresh token invalide ou révoqué");
        }

        UserAccount user = userAccountRepository.findByIdWithCompanyAndRoles(stored.getUser().getId())
                .orElseThrow(() -> unauthorized("Utilisateur introuvable"));
        if (!user.isEnabled()) {
            throw unauthorized("Compte désactivé");
        }

        stored.setRevokedAt(now);
        refreshTokenRepository.save(stored);

        DocuForgePrincipal principal = toPrincipal(user);
        return new TokenResponse(
                jwtService.createAccessToken(principal),
                issueRefreshToken(user),
                "Bearer",
                jwtProperties.accessExpirationSeconds()
        );
    }

    @Transactional
    public void logout(UUID userId, String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
            if (token.getUser().getId().equals(userId) && token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        UserAccount user = userAccountRepository.findByIdWithCompanyAndRoles(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCompany().getId(),
                user.getCompany().getIdentifier(),
                user.getCompany().getName(),
                user.getRoles().stream().map(role -> role.getCode()).collect(Collectors.toCollection(LinkedHashSet::new))
        );
    }

    private String issueRefreshToken(UserAccount user) {
        String raw = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(hash(raw));
        entity.setExpiresAt(Instant.now().plusSeconds(jwtProperties.refreshExpirationSeconds()));
        refreshTokenRepository.save(entity);
        return raw;
    }

    private DocuForgePrincipal toPrincipal(UserAccount user) {
        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getCode())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new DocuForgePrincipal(
                user.getId(),
                user.getCompany().getId(),
                user.getCompany().getIdentifier(),
                user.getEmail(),
                user.getPasswordHash(),
                user.isEnabled(),
                roles
        );
    }

    private void writeAudit(UserAccount user, String action, String status, String ipAddress) {
        auditService.recordForUser(
                user,
                action,
                "USER",
                user.getId().toString(),
                status,
                truncateIp(ipAddress),
                null
        );
    }

    private static String truncateIp(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }
        return ipAddress.length() <= 45 ? ipAddress : ipAddress.substring(0, 45);
    }

    private static ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}