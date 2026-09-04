package ai.docuforge.config;

import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.user.Role;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DevAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminBootstrap.class);

    private final BootstrapProperties properties;
    private final CompanyRepository companyRepository;
    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DevAdminBootstrap(
            BootstrapProperties properties,
            CompanyRepository companyRepository,
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.companyRepository = companyRepository;
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (userAccountRepository.count() > 0) {
            return;
        }

        Company company = companyRepository.findByIdentifier(properties.companyIdentifier())
                .orElseGet(() -> {
                    Company created = new Company();
                    created.setName(properties.companyName());
                    created.setIdentifier(properties.companyIdentifier());
                    return companyRepository.save(created);
                });

        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN.name())
                .orElseThrow(() -> new IllegalStateException("Role ADMIN missing — run Flyway V1"));

        UserAccount admin = new UserAccount();
        admin.setCompany(company);
        admin.setEmail(properties.adminEmail());
        admin.setPasswordHash(passwordEncoder.encode(properties.adminPassword()));
        admin.setFirstName(properties.adminFirstName());
        admin.setLastName(properties.adminLastName());
        admin.setEnabled(true);
        admin.getRoles().add(adminRole);
        userAccountRepository.save(admin);

        log.info(
                "Bootstrap admin created company={} email={} (change password before production)",
                company.getIdentifier(),
                admin.getEmail()
        );
    }
}