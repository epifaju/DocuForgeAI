package ai.docuforge.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionSafetyValidatorTest {

    @Test
    void skipsNonProduction() {
        assertThat(ProductionSafetyValidator.isProduction("development")).isFalse();
        assertThat(ProductionSafetyValidator.validate(
                "development",
                "changeme_generate_a_long_random_secret_at_least_32_chars",
                "changeme_postgres_dev_only",
                true,
                "changeme_admin_dev_only"
        )).isEmpty();
    }

    @Test
    void detectsProductionAliases() {
        assertThat(ProductionSafetyValidator.isProduction("production")).isTrue();
        assertThat(ProductionSafetyValidator.isProduction("PROD")).isTrue();
    }

    @Test
    void rejectsWeakSecretsAndBootstrapInProduction() {
        List<String> problems = ProductionSafetyValidator.validate(
                "production",
                "changeme_generate_a_long_random_secret_at_least_32_chars",
                "changeme_postgres_dev_only",
                true,
                "changeme_admin_dev_only"
        );
        assertThat(problems).hasSizeGreaterThanOrEqualTo(3);
        assertThat(problems).anyMatch(p -> p.contains("JWT_SECRET"));
        assertThat(problems).anyMatch(p -> p.contains("POSTGRES_PASSWORD"));
        assertThat(problems).anyMatch(p -> p.contains("BOOTSTRAP_ENABLED"));
    }

    @Test
    void acceptsStrongProductionConfig() {
        List<String> problems = ProductionSafetyValidator.validate(
                "production",
                "a".repeat(48),
                "Str0ng-Postgres-Passw0rd!",
                false,
                "unused-because-bootstrap-off"
        );
        assertThat(problems).isEmpty();
    }

    @Test
    void rejectsShortJwt() {
        List<String> problems = ProductionSafetyValidator.validate(
                "production",
                "short-secret",
                "Str0ng-Postgres-Passw0rd!",
                false,
                "x"
        );
        assertThat(problems).anyMatch(p -> p.contains("32"));
    }
}
