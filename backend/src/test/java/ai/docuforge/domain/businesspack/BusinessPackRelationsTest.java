package ai.docuforge.domain.businesspack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateOrigin;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateStatus;
import ai.docuforge.domain.template.TemplateVersion;
import ai.docuforge.domain.template.TemplateVersionRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class BusinessPackRelationsTest {

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
    private CompanyRepository companyRepository;

    @Autowired
    private BusinessPackRepository businessPackRepository;

    @Autowired
    private BusinessPackVersionRepository businessPackVersionRepository;

    @Autowired
    private BusinessPackTemplateRepository businessPackTemplateRepository;

    @Autowired
    private BusinessPackPromptRepository businessPackPromptRepository;

    @Autowired
    private BusinessPackFileRepository businessPackFileRepository;

    @Autowired
    private BusinessPackInstallationRepository businessPackInstallationRepository;

    @Autowired
    private PackImportJobRepository packImportJobRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private TemplateVersionRepository templateVersionRepository;

    @Test
    void flywayCreatedRelationTablesAndTemplateOriginColumns() {
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

        Integer originCols = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'templates'
                  AND column_name IN ('origin', 'source_pack_id')
                """,
                Integer.class
        );
        assertThat(originCols).isEqualTo(2);

        Integer versionCols = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'template_versions'
                  AND column_name IN ('source_pack_version_id', 'source_template_code')
                """,
                Integer.class
        );
        assertThat(versionCols).isEqualTo(2);
    }

    @Test
    @Transactional
    void persistsPackRelationsWithTemplateProvenance() {
        Company company = new Company();
        company.setName("Pack Relations Demo");
        company.setIdentifier("pack-rel-" + UUID.randomUUID());
        company = companyRepository.saveAndFlush(company);

        BusinessPack pack = new BusinessPack();
        pack.setCompany(company);
        pack.setPackKey("com.acme.docuforge.pack.demo");
        pack.setSlug("demo");
        pack.setName("Demo Pack");
        pack.setPackType(BusinessPackType.CUSTOM);
        pack.setStatus(BusinessPackStatus.INSTALLED);
        pack = businessPackRepository.saveAndFlush(pack);

        BusinessPackVersion packVersion = new BusinessPackVersion();
        packVersion.setBusinessPack(pack);
        packVersion.setVersion("1.0.0");
        packVersion.setSchemaVersion("DBPF-1");
        packVersion.setManifest("{\"schemaVersion\":\"DBPF-1\",\"id\":\"com.acme.docuforge.pack.demo\"}");
        packVersion.setStatus(PackVersionStatus.INSTALLED);
        packVersion = businessPackVersionRepository.saveAndFlush(packVersion);

        Template template = new Template();
        template.setCompany(company);
        template.setCode("DEMO_DEVIS");
        template.setName("Devis Demo");
        template.setStatus(TemplateStatus.ACTIVE);
        template.setOrigin(TemplateOrigin.PACK);
        template.setSourcePack(pack);
        template = templateRepository.saveAndFlush(template);

        TemplateVersion templateVersion = new TemplateVersion();
        templateVersion.setTemplate(template);
        templateVersion.setVersionNumber(1);
        templateVersion.setOriginalFilename("demo-devis.docx");
        templateVersion.setStorageKey("templates/" + UUID.randomUUID() + ".docx");
        templateVersion.setChecksum("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        templateVersion.setSourcePackVersion(packVersion);
        templateVersion.setSourceTemplateCode("DEMO_DEVIS");
        templateVersion = templateVersionRepository.saveAndFlush(templateVersion);

        template.setCurrentVersion(templateVersion);
        templateRepository.saveAndFlush(template);

        BusinessPackTemplate link = new BusinessPackTemplate();
        link.setBusinessPackVersion(packVersion);
        link.setTemplate(template);
        link.setTemplateVersion(templateVersion);
        link.setTemplateCode("DEMO_DEVIS");
        link.setEnabledByDefault(true);
        businessPackTemplateRepository.saveAndFlush(link);

        BusinessPackPrompt prompt = new BusinessPackPrompt();
        prompt.setBusinessPackVersion(packVersion);
        prompt.setPromptCode("DEMO_PROMPT");
        prompt.setPromptVersion("1.0.0");
        prompt.setContent("Rewrite the work description in a professional tone.");
        prompt.setChecksum("sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        businessPackPromptRepository.saveAndFlush(prompt);

        BusinessPackFile file = new BusinessPackFile();
        file.setBusinessPackVersion(packVersion);
        file.setLogicalPath("templates/demo-devis.docx");
        file.setFileType(PackFileType.TEMPLATE);
        file.setStorageKey("templates/" + UUID.randomUUID() + ".docx");
        file.setChecksum("sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
        file.setSizeBytes(128L);
        businessPackFileRepository.saveAndFlush(file);

        BusinessPackInstallation installation = new BusinessPackInstallation();
        installation.setCompany(company);
        installation.setBusinessPack(pack);
        installation.setBusinessPackVersion(packVersion);
        installation.setInstallationType(PackInstallationType.FRESH);
        installation.setStatus(PackInstallationStatus.ACTIVE);
        installation.setMetadata("{\"source\":\"test\"}");
        businessPackInstallationRepository.saveAndFlush(installation);

        PackImportJob job = new PackImportJob();
        job.setCompany(company);
        job.setOriginalFilename("docuforge-pack-demo-1.0.0.zip");
        job.setStatus(PackImportJobStatus.UPLOADED);
        job.setDetectedPackKey("com.acme.docuforge.pack.demo");
        job.setDetectedVersion("1.0.0");
        packImportJobRepository.saveAndFlush(job);

        assertThat(businessPackTemplateRepository
                .findByBusinessPackVersionIdAndTemplateCode(packVersion.getId(), "DEMO_DEVIS"))
                .isPresent();
        assertThat(templateRepository.findById(template.getId()).orElseThrow().getOrigin())
                .isEqualTo(TemplateOrigin.PACK);
        assertThat(templateVersionRepository.findById(templateVersion.getId()).orElseThrow().getSourceTemplateCode())
                .isEqualTo("DEMO_DEVIS");
        assertThat(packImportJobRepository.findByCompanyIdAndStatus(company.getId(), PackImportJobStatus.UPLOADED))
                .hasSize(1);
    }

    @Test
    @Transactional
    void rejectsDuplicateTemplateCodeForSamePackVersion() {
        Company company = new Company();
        company.setName("Dup Demo");
        company.setIdentifier("dup-" + UUID.randomUUID());
        company = companyRepository.saveAndFlush(company);

        BusinessPack pack = new BusinessPack();
        pack.setCompany(company);
        pack.setPackKey("com.acme.docuforge.pack.dup");
        pack.setSlug("dup");
        pack.setName("Dup");
        pack.setPackType(BusinessPackType.CUSTOM);
        pack.setStatus(BusinessPackStatus.INSTALLED);
        pack = businessPackRepository.saveAndFlush(pack);

        BusinessPackVersion packVersion = new BusinessPackVersion();
        packVersion.setBusinessPack(pack);
        packVersion.setVersion("1.0.0");
        packVersion.setSchemaVersion("DBPF-1");
        packVersion.setManifest("{}");
        packVersion.setStatus(PackVersionStatus.VALID);
        packVersion = businessPackVersionRepository.saveAndFlush(packVersion);

        Template t1 = userTemplate(company, "T1");
        TemplateVersion v1 = templateVersion(t1, 1);
        Template t2 = userTemplate(company, "T2");
        TemplateVersion v2 = templateVersion(t2, 1);

        BusinessPackTemplate first = packTemplate(packVersion, t1, v1, "SAME_CODE");
        businessPackTemplateRepository.saveAndFlush(first);

        BusinessPackTemplate duplicate = packTemplate(packVersion, t2, v2, "SAME_CODE");
        assertThatThrownBy(() -> businessPackTemplateRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Template userTemplate(Company company, String code) {
        Template template = new Template();
        template.setCompany(company);
        template.setCode(code);
        template.setName(code);
        template.setStatus(TemplateStatus.DRAFT);
        template.setOrigin(TemplateOrigin.USER);
        return templateRepository.saveAndFlush(template);
    }

    private TemplateVersion templateVersion(Template template, int number) {
        TemplateVersion version = new TemplateVersion();
        version.setTemplate(template);
        version.setVersionNumber(number);
        version.setOriginalFilename(template.getCode() + ".docx");
        version.setStorageKey("templates/" + UUID.randomUUID() + ".docx");
        version.setChecksum("dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd");
        return templateVersionRepository.saveAndFlush(version);
    }

    private static BusinessPackTemplate packTemplate(
            BusinessPackVersion packVersion,
            Template template,
            TemplateVersion templateVersion,
            String code
    ) {
        BusinessPackTemplate link = new BusinessPackTemplate();
        link.setBusinessPackVersion(packVersion);
        link.setTemplate(template);
        link.setTemplateVersion(templateVersion);
        link.setTemplateCode(code);
        return link;
    }
}
