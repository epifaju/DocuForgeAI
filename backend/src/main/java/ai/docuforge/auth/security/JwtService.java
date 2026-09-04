package ai.docuforge.auth.security;

import ai.docuforge.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String createAccessToken(DocuForgePrincipal principal) {
        Instant now = Instant.now();
        return createAccessToken(principal, now, now.plusSeconds(properties.accessExpirationSeconds()));
    }

    public String createAccessToken(DocuForgePrincipal principal, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(principal.getUserId().toString())
                .claim("type", "access")
                .claim("email", principal.getUsername())
                .claim("companyId", principal.getCompanyId().toString())
                .claim("companyIdentifier", principal.getCompanyIdentifier())
                .claim("roles", principal.getRoles().stream().sorted().toList())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        if (!"access".equals(claims.get("type", String.class))) {
            throw new JwtAuthenticationException("Token type invalide");
        }
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        return new AccessTokenClaims(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get("companyId", String.class)),
                claims.get("companyIdentifier", String.class),
                claims.get("email", String.class),
                roles == null ? List.of() : roles,
                claims.getExpiration().toInstant()
        );
    }

    public boolean isExpired(ExpiredJwtException ex) {
        return ex != null;
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new JwtAuthenticationException("Token expirÃ©", ex);
        } catch (MalformedJwtException | SignatureException | IllegalArgumentException ex) {
            throw new JwtAuthenticationException("Token invalide", ex);
        }
    }

    public record AccessTokenClaims(
            UUID userId,
            UUID companyId,
            String companyIdentifier,
            String email,
            List<String> roles,
            Instant expiresAt
    ) {
    }
}