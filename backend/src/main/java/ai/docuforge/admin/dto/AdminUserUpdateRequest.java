package ai.docuforge.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record AdminUserUpdateRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotEmpty Set<String> roles,
        boolean enabled,
        @Size(min = 8, max = 100) String password
) {
}
