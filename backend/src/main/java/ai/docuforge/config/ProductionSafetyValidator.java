package ai.docuforge.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuse to start in production with weak / placeholder secrets or bootstrap enabled.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionSafetyValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyValidator.class);

    static final Set<String> WEAK_JWT = Set.of(
            "changeme_generate_a_long_random_secret_at_least_32_chars",
            "changeme",
            "secret",
            "password"
    );

    static final Set<String> WEAK_PASSWORDS = Set.of(
            "changeme_postgres_dev_only",
            "changeme_admin_dev_only",
            "changeme",
            "password",
            "admin",
            "postgres"
    );

    private final DocuForgeProperties properties;
    private final JwtProperties jwtProperties;
    private final BootstrapProperties bootstrapProperties;
    private final Environment environment;

    public ProductionSafetyValidator(
            DocuForgeProperties properties,
            JwtProperties jwtProperties,
            BootstrapProperties bootstrapProperties,
            Environment environment
    ) {
        this.properties = properties;
        this.jwtProperties = jwtProperties;
        this.bootstrapProperties = bootstrapProperties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProduction(properties.appEnv())) {
            return;
        }
        List<String> problems = validate(
                properties.appEnv(),
                jwtProperties.secret(),
                environment.getProperty("SPRING_DATASOURCE_PASSWORD",
                        environment.getProperty("spring.datasource.password", "")),
                bootstrapProperties.enabled(),
                bootstrapProperties.adminPassword()
        );
        if (!problems.isEmpty()) {
            problems.forEach(p -> log.error("Production safety: {}", p));
            throw new IllegalStateException(
                    "Refusing to start: production safety checks failed (" + problems.size() + "). See logs."
            );
        }
        log.info("Production safety checks passed (APP_ENV={})", properties.appEnv());
    }

    static boolean isProduction(String appEnv) {
        if (appEnv == null || appEnv.isBlank()) {
            return false;
        }
        String normalized = appEnv.trim().toLowerCase(Locale.ROOT);
        return "production".equals(normalized) || "prod".equals(normalized);
    }

    static List<String> validate(
            String appEnv,
            String jwtSecret,
            String postgresPassword,
            boolean bootstrapEnabled,
            String bootstrapAdminPassword
    ) {
        List<String> problems = new ArrayList<>();
        if (!isProduction(appEnv)) {
            return problems;
        }
        if (jwtSecret == null || jwtSecret.isBlank()) {
            problems.add("JWT_SECRET is missing");
        } else if (jwtSecret.length() < 32) {
            problems.add("JWT_SECRET must be at least 32 characters");
        } else if (WEAK_JWT.contains(jwtSecret) || jwtSecret.toLowerCase(Locale.ROOT).startsWith("changeme")) {
            problems.add("JWT_SECRET is a known placeholder — run scripts/secure-env");
        }
        if (postgresPassword == null || postgresPassword.isBlank()) {
            problems.add("POSTGRES_PASSWORD / datasource password is missing");
        } else if (WEAK_PASSWORDS.contains(postgresPassword)
                || postgresPassword.toLowerCase(Locale.ROOT).startsWith("changeme")) {
            problems.add("POSTGRES_PASSWORD is a known placeholder — rotate with secure-env -RotatePostgres");
        }
        if (bootstrapEnabled) {
            problems.add("DOCUFORGE_BOOTSTRAP_ENABLED must be false in production");
            if (bootstrapAdminPassword != null
                    && (WEAK_PASSWORDS.contains(bootstrapAdminPassword)
                            || bootstrapAdminPassword.toLowerCase(Locale.ROOT).startsWith("changeme"))) {
                problems.add("DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD is still a placeholder");
            }
        }
        return problems;
    }
}
