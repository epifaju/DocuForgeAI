package ai.docuforge.businesspack.compatibility;

import ai.docuforge.businesspack.manifest.PackManifest;
import ai.docuforge.businesspack.manifest.PackValidationIssue;
import ai.docuforge.config.PackProperties;
import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.Semver.SemverType;
import com.vdurmont.semver4j.SemverException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * DocuForge platform SemVer compatibility and pack-version downgrade helpers (PRD §§70–71).
 */
@Component
public class PackCompatibilityService {

    private static final Pattern MAJOR_RANGE = Pattern.compile("^(0|[1-9][0-9]*)\\.x$", Pattern.CASE_INSENSITIVE);

    private final PackProperties packProperties;

    public PackCompatibilityService(PackProperties packProperties) {
        this.packProperties = packProperties;
    }

    public List<PackValidationIssue> validatePlatformCompatibility(PackManifest.Compatibility compatibility) {
        return validatePlatformCompatibility(compatibility, packProperties.normalizedPlatformVersion());
    }

    public List<PackValidationIssue> validatePlatformCompatibility(
            PackManifest.Compatibility compatibility,
            String platformVersion
    ) {
        List<PackValidationIssue> issues = new ArrayList<>();
        if (compatibility == null || !StringUtils.hasText(compatibility.minimumDocuForgeVersion())) {
            issues.add(PackValidationIssue.error(
                    "PACK_INCOMPATIBLE_DOCUFORGE_VERSION",
                    "error.pack.incompatible_docuforge_version"
            ));
            return List.copyOf(issues);
        }

        Semver platform;
        try {
            platform = parseNpm(platformVersion);
        } catch (SemverException | IllegalArgumentException ex) {
            issues.add(PackValidationIssue.error(
                    "PACK_INCOMPATIBLE_DOCUFORGE_VERSION",
                    "error.pack.incompatible_docuforge_version"
            ));
            return List.copyOf(issues);
        }

        try {
            Semver minimum = parseNpm(compatibility.minimumDocuForgeVersion());
            if (platform.isLowerThan(minimum)) {
                issues.add(incompatible());
                return List.copyOf(issues);
            }
        } catch (SemverException | IllegalArgumentException ex) {
            issues.add(incompatible());
            return List.copyOf(issues);
        }

        String maximum = compatibility.maximumDocuForgeVersion();
        if (StringUtils.hasText(maximum) && !"null".equalsIgnoreCase(maximum.trim())) {
            String max = maximum.trim();
            try {
                if (MAJOR_RANGE.matcher(max).matches()) {
                    String major = max.substring(0, max.toLowerCase(Locale.ROOT).indexOf(".x"));
                    if (!platform.satisfies(major + ".x")) {
                        issues.add(incompatible());
                    }
                } else {
                    Semver maxSemver = parseNpm(max);
                    if (platform.isGreaterThan(maxSemver)) {
                        issues.add(incompatible());
                    }
                }
            } catch (SemverException | IllegalArgumentException ex) {
                issues.add(incompatible());
            }
        }

        return List.copyOf(issues);
    }

    /**
     * {@code true} when installing {@code candidatePackVersion} would be a downgrade vs
     * {@code installedPackVersion} (PRD §71). Controlled admin rollback is out of scope.
     */
    public boolean isDowngrade(String installedPackVersion, String candidatePackVersion) {
        if (!StringUtils.hasText(installedPackVersion) || !StringUtils.hasText(candidatePackVersion)) {
            return false;
        }
        try {
            Semver installed = parseStrict(installedPackVersion);
            Semver candidate = parseStrict(candidatePackVersion);
            return candidate.isLowerThan(installed);
        } catch (SemverException | IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * {@code true} when {@code candidatePackVersion} is strictly greater than {@code installedPackVersion}.
     */
    public boolean isUpgrade(String installedPackVersion, String candidatePackVersion) {
        if (!StringUtils.hasText(installedPackVersion) || !StringUtils.hasText(candidatePackVersion)) {
            return false;
        }
        try {
            Semver installed = parseStrict(installedPackVersion);
            Semver candidate = parseStrict(candidatePackVersion);
            return candidate.isGreaterThan(installed);
        } catch (SemverException | IllegalArgumentException ex) {
            return false;
        }
    }

    public PackValidationIssue downgradeNotAllowedIssue() {
        return PackValidationIssue.error("PACK_DOWNGRADE_NOT_ALLOWED", "error.pack.downgrade_not_allowed");
    }

    public Semver.VersionDiff classifyUpdate(String fromVersion, String toVersion) {
        Semver from = parseStrict(fromVersion);
        Semver to = parseStrict(toVersion);
        return from.diff(to);
    }

    /**
     * Maps semver4j diff to MAJOR / MINOR / PATCH / SAME for API responses.
     */
    public String classifyUpdateKind(String fromVersion, String toVersion) {
        try {
            Semver.VersionDiff diff = classifyUpdate(fromVersion, toVersion);
            return switch (diff) {
                case MAJOR -> "MAJOR";
                case MINOR -> "MINOR";
                case PATCH -> "PATCH";
                default -> "SAME";
            };
        } catch (SemverException | IllegalArgumentException ex) {
            return "UNKNOWN";
        }
    }

    private static PackValidationIssue incompatible() {
        return PackValidationIssue.error(
                "PACK_INCOMPATIBLE_DOCUFORGE_VERSION",
                "error.pack.incompatible_docuforge_version"
        );
    }

    private static Semver parseNpm(String version) {
        return new Semver(stripSnapshot(version), SemverType.NPM);
    }

    private static Semver parseStrict(String version) {
        return new Semver(stripSnapshot(version), SemverType.STRICT);
    }

    private static String stripSnapshot(String version) {
        String value = version.trim();
        int snap = value.toUpperCase(Locale.ROOT).indexOf("-SNAPSHOT");
        if (snap > 0) {
            return value.substring(0, snap);
        }
        return value;
    }
}
