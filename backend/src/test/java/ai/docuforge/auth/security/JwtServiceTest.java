package ai.docuforge.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-characters!!";

    private JwtService jwtService;
    private DocuForgePrincipal principal;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, 900, 604_800));
        principal = new DocuForgePrincipal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "acme",
                "admin@acme.test",
                "hash",
                true,
                Set.of("ADMIN", "EDITOR")
        );
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 900, 604_800)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void roundTripsAccessTokenClaims() {
        String token = jwtService.createAccessToken(principal);
        JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(token);

        assertThat(claims.userId()).isEqualTo(principal.getUserId());
        assertThat(claims.companyId()).isEqualTo(principal.getCompanyId());
        assertThat(claims.companyIdentifier()).isEqualTo("acme");
        assertThat(claims.email()).isEqualTo("admin@acme.test");
        assertThat(claims.roles()).containsExactly("ADMIN", "EDITOR");
        assertThat(claims.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void rejectsNonAccessTokenType() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String refreshStyle = Jwts.builder()
                .subject(principal.getUserId().toString())
                .claim("type", "refresh")
                .claim("email", principal.getUsername())
                .claim("companyId", principal.getCompanyId().toString())
                .claim("companyIdentifier", principal.getCompanyIdentifier())
                .claim("roles", principal.getRoles().stream().sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAccessToken(refreshStyle))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageContaining("type");
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> jwtService.parseAccessToken("not-a-jwt"))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    void rejectsBadSignature() {
        JwtService other = new JwtService(new JwtProperties(
                "another-secret-key-with-at-least-32-chars!", 900, 604_800
        ));
        String token = other.createAccessToken(principal);

        assertThatThrownBy(() -> jwtService.parseAccessToken(token))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    void rejectsExpiredToken() {
        Instant now = Instant.now();
        String expired = jwtService.createAccessToken(
                principal, now.minusSeconds(3600), now.minusSeconds(10)
        );

        assertThatThrownBy(() -> jwtService.parseAccessToken(expired))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageContaining("pir");
    }
}
