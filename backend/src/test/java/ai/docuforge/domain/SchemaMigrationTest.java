package ai.docuforge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class SchemaMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine")
            .withDatabaseName("docuforge")
            .withUsername("docuforge")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Test
    void flywayCreatedExpectedTablesAndSeededRoles() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'companies','users','roles','user_roles',
                    'templates','template_versions','template_variables',
                    'generated_documents','batch_jobs','audit_logs'
                  )
                """,
                Integer.class
        );
        assertThat(tableCount).isEqualTo(10);
        assertThat(roleRepository.count()).isEqualTo(4);
        assertThat(roleRepository.findByCode(RoleCode.ADMIN.name())).isPresent();
    }

    @Test
    void canPersistCompanyAndUserAgainstMigratedSchema() {
        Company company = new Company();
        company.setName("Acme SARL");
        company.setIdentifier("acme-" + UUID.randomUUID());
        company = companyRepository.saveAndFlush(company);

        UserAccount user = new UserAccount();
        user.setCompany(company);
        user.setEmail("admin@example.test");
        user.setPasswordHash("{noop}not-a-real-hash");
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setEnabled(true);
        user.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN.name()).orElseThrow());
        user = userAccountRepository.saveAndFlush(user);

        assertThat(user.getId()).isNotNull();
        assertThat(userAccountRepository.findByCompanyIdAndEmail(company.getId(), "admin@example.test")).isPresent();

        Integer lineageCols = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'generated_documents'
                  AND column_name IN ('root_document_id','parent_document_id','document_version_number')
                """,
                Integer.class
        );
        assertThat(lineageCols).isEqualTo(3);

        Integer aiTables = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'ai_requests'
                """,
                Integer.class
        );
        assertThat(aiTables).isEqualTo(1);

        Integer batchItemTables = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'batch_items'
                """,
                Integer.class
        );
        assertThat(batchItemTables).isEqualTo(1);

        Integer emailTables = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'email_deliveries'
                """,
                Integer.class
        );
        assertThat(emailTables).isEqualTo(1);

        Integer settingsTables = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'application_settings'
                """,
                Integer.class
        );
        assertThat(settingsTables).isEqualTo(1);

        Integer passwordResetTables = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'password_reset_tokens'
                """,
                Integer.class
        );
        assertThat(passwordResetTables).isEqualTo(1);

        Integer businessPackTables = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('business_packs', 'business_pack_versions')
                """,
                Integer.class
        );
        assertThat(businessPackTables).isEqualTo(2);

        Integer packCompanyColumn = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'business_packs'
                  AND column_name = 'company_id'
                """,
                Integer.class
        );
        assertThat(packCompanyColumn).isEqualTo(1);

        Integer relationTables = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'business_pack_templates',
                    'business_pack_prompts',
                    'business_pack_files',
                    'business_pack_installations',
                    'pack_import_jobs'
                  )
                """,
                Integer.class
        );
        assertThat(relationTables).isEqualTo(5);

        Integer stagingCol = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'pack_import_jobs'
                  AND column_name IN ('staging_storage_key', 'expires_at')
                """,
                Integer.class
        );
        assertThat(stagingCol).isEqualTo(2);
    }
}