package ai.docuforge.template.parser;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * PRD §69 — variable key convention.
 */
public final class VariableKeyRules {

    public static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.]{0,199}$");

    private VariableKeyRules() {
    }

    public static boolean isValid(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String trimmed = key.trim();
        if (!KEY_PATTERN.matcher(trimmed).matches()) {
            return false;
        }
        if (trimmed.contains("..") || trimmed.contains("/") || trimmed.contains("\\")) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("javascript:")) {
            return false;
        }
        // Reject the path segment "script" only — not substrings like "description".
        for (String segment : lower.split("\\.")) {
            if ("script".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }
}