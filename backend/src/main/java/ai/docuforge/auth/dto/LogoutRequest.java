package ai.docuforge.auth.dto;

/**
 * Refresh token may come from httpOnly cookie when body is empty.
 */
public record LogoutRequest(String refreshToken) {
}
