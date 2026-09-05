package ai.docuforge.auth.dto;

/**
 * Refresh token may come from httpOnly cookie when body is empty.
 */
public record RefreshRequest(String refreshToken) {
}
