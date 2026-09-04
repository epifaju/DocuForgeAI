package ai.docuforge.admin;

import ai.docuforge.admin.dto.AdminUserCreateRequest;
import ai.docuforge.admin.dto.AdminUserResponse;
import ai.docuforge.admin.dto.AdminUserUpdateRequest;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.PageResponse;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.user.Role;
import ai.docuforge.domain.user.RoleCode;
import ai.docuforge.domain.user.RoleRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {

    private final UserAccountRepository userAccountRepository;
    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(
            UserAccountRepository userAccountRepository,
            CompanyRepository companyRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.companyRepository = companyRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(DocuForgePrincipal principal, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<UserAccount> result = userAccountRepository.findByCompanyId(
                principal.getCompanyId(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "email"))
        );
        return new PageResponse<>(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    public AdminUserResponse create(DocuForgePrincipal principal, AdminUserCreateRequest request) {
        Company company = companyRepository
                .findById(principal.getCompanyId())
                .orElseThrow(() -> notFound("Societe introuvable"));

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userAccountRepository.findByCompanyIdAndEmail(company.getId(), email).isPresent()) {
            throw conflict("Un utilisateur avec cet email existe deja.");
        }

        UserAccount user = new UserAccount();
        user.setCompany(company);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEnabled(request.enabled() == null || request.enabled());
        user.setRoles(resolveRoles(request.roles()));
        return toResponse(userAccountRepository.save(user));
    }

    @Transactional
    public AdminUserResponse update(DocuForgePrincipal principal, UUID userId, AdminUserUpdateRequest request) {
        UserAccount user = userAccountRepository
                .findByIdAndCompanyIdWithRoles(userId, principal.getCompanyId())
                .orElseThrow(() -> notFound("Utilisateur introuvable"));

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEnabled(request.enabled());
        user.setRoles(resolveRoles(request.roles()));
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        if (!user.isEnabled() && user.getId().equals(principal.getUserId())) {
            throw conflict("Vous ne pouvez pas vous desactiver vous-meme.");
        }

        return toResponse(userAccountRepository.save(user));
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        Set<Role> roles = new HashSet<>();
        for (String raw : codes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String code = raw.trim().toUpperCase(Locale.ROOT);
            try {
                RoleCode.valueOf(code);
            } catch (IllegalArgumentException ex) {
                throw badRequest("Role invalide: " + code);
            }
            Role role = roleRepository
                    .findByCode(code)
                    .orElseThrow(() -> badRequest("Role inconnu: " + code));
            roles.add(role);
        }
        if (roles.isEmpty()) {
            throw badRequest("Au moins un role est requis.");
        }
        return roles;
    }

    private AdminUserResponse toResponse(UserAccount user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isEnabled(),
                roles,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
