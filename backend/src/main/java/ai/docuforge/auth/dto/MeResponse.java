package ai.docuforge.auth.dto;

import java.util.Set;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UUID companyId,
        String companyIdentifier,
        String companyName,
        Set<String> roles
) {
}