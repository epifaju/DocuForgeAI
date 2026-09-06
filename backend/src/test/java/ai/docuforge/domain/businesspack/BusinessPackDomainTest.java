package ai.docuforge.domain.businesspack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class BusinessPackDomainTest {

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
    private CompanyRepository companyRepository;

    @Autowired
    private BusinessPackRepository businessPackRepository;

    @Autowired
    private BusinessPackVersionRepository businessPackVersionRepository;

    @Test
    @Transactional
    void persistsOfficialPackWithVersionAndCurrentPointer() {
        BusinessPack pack = new BusinessPack();
        pack.setCompany(null);
        pack.setPackKey("com.docuforge.pack.artisan");
        pack.setSlug("artisan");
        pack.setName("Pack Artisan");
        pack.setDescription("Demo pack (fictional).");
        pack.setPackType(BusinessPackType.OFFICIAL);
        pack.setPublisherId("docuforge");
        pack.setPublisherName("DocuForge AI");
        pack.setStatus(BusinessPackStatus.INSTALLED);
        pack = businessPackRepository.saveAndFlush(pack);

        BusinessPackVersion version = new BusinessPackVersion();
        version.setBusinessPack(pack);
        version.setVersion("1.0.0");
        version.setSchemaVersion("DBPF-1");
        version.setManifest("""
                {"schemaVersion":"DBPF-1","id":"com.docuforge.pack.artisan","version":"1.0.0"}
                """);
        version.setMinimumDocuForgeVersion("0.1.0");
        version.setArchiveChecksum("sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        version.setStatus(PackVersionStatus.INSTALLED);
        version = businessPackVersionRepository.saveAndFlush(version);

        pack.setCurrentVersion(version);
        businessPackRepository.saveAndFlush(pack);

        BusinessPack reloaded = businessPackRepository.findByCompanyIsNullAndPackKey("com.docuforge.pack.artisan")
                .orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(pack.getId());
        assertThat(reloaded.getCurrentVersion().getId()).isEqualTo(version.getId());
        assertThat(reloaded.getPackType()).isEqualTo(BusinessPackType.OFFICIAL);
        assertThat(businessPackVersionRepository.findByBusinessPackIdAndVersion(pack.getId(), "1.0.0")).isPresent();
    }

    @Test
    @Transactional
    void enforcesUniquePackKeyPerCompany() {
        Company company = new Company();
        company.setName("Acme Demo SARL");
        company.setIdentifier("acme-" + UUID.randomUUID());
        company = companyRepository.saveAndFlush(company);

        BusinessPack first = companyPack(company, "com.acme.docuforge.pack.sales");
        businessPackRepository.saveAndFlush(first);

        BusinessPack duplicate = companyPack(company, "com.acme.docuforge.pack.sales");
        assertThatThrownBy(() -> businessPackRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void enforcesUniqueGlobalPackKeyWhenCompanyNull() {
        BusinessPack first = globalPack("com.docuforge.pack.rh");
        businessPackRepository.saveAndFlush(first);

        BusinessPack duplicate = globalPack("com.docuforge.pack.rh");
        assertThatThrownBy(() -> businessPackRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void enforcesUniqueVersionPerPack() {
        BusinessPack pack = globalPack("com.docuforge.pack.association");
        pack = businessPackRepository.saveAndFlush(pack);

        BusinessPackVersion v1 = version(pack, "1.0.0");
        businessPackVersionRepository.saveAndFlush(v1);

        BusinessPackVersion duplicate = version(pack, "1.0.0");
        assertThatThrownBy(() -> businessPackVersionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static BusinessPack companyPack(Company company, String packKey) {
        BusinessPack pack = new BusinessPack();
        pack.setCompany(company);
        pack.setPackKey(packKey);
        pack.setSlug("custom-sales");
        pack.setName("Custom Sales");
        pack.setPackType(BusinessPackType.CUSTOM);
        pack.setStatus(BusinessPackStatus.INSTALLED);
        return pack;
    }

    private static BusinessPack globalPack(String packKey) {
        BusinessPack pack = new BusinessPack();
        pack.setPackKey(packKey);
        pack.setSlug(packKey.substring(packKey.lastIndexOf('.') + 1));
        pack.setName(packKey);
        pack.setPackType(BusinessPackType.OFFICIAL);
        pack.setStatus(BusinessPackStatus.INSTALLED);
        return pack;
    }

    private static BusinessPackVersion version(BusinessPack pack, String semver) {
        BusinessPackVersion version = new BusinessPackVersion();
        version.setBusinessPack(pack);
        version.setVersion(semver);
        version.setSchemaVersion("DBPF-1");
        version.setManifest("{\"schemaVersion\":\"DBPF-1\"}");
        version.setStatus(PackVersionStatus.VALID);
        return version;
    }
}
