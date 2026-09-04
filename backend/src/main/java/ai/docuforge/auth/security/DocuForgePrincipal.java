package ai.docuforge.auth.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class DocuForgePrincipal implements UserDetails {

    private final UUID userId;
    private final UUID companyId;
    private final String companyIdentifier;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final Set<String> roles;

    public DocuForgePrincipal(
            UUID userId,
            UUID companyId,
            String companyIdentifier,
            String email,
            String passwordHash,
            boolean enabled,
            Set<String> roles
    ) {
        this.userId = userId;
        this.companyId = companyId;
        this.companyIdentifier = companyIdentifier;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.roles = Set.copyOf(roles);
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getCompanyIdentifier() {
        return companyIdentifier;
    }

    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}