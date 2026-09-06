package ai.docuforge.businesspack.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docuforge.businesspack.manifest.PackManifest;
import ai.docuforge.businesspack.manifest.PackValidationIssue;
import ai.docuforge.config.PackProperties;
import com.vdurmont.semver4j.Semver;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PackCompatibilityServiceTest {

    private PackCompatibilityService service;

    @BeforeEach
    void setUp() {
        PackProperties props = new PackProperties(
                true, 100, 250, 500, 100, 25, 50, 24, "1.5.0", Set.of("json")
        );
        service = new PackCompatibilityService(props);
    }

    @Test
    void compatibleWithinMinAndMajorRange() {
        var issues = service.validatePlatformCompatibility(
                new PackManifest.Compatibility("1.4.0", "1.x")
        );
        assertThat(issues).isEmpty();
    }

    @Test
    void compatibleWithNullMaximum() {
        var issues = service.validatePlatformCompatibility(
                new PackManifest.Compatibility("0.1.0", null)
        );
        assertThat(issues).isEmpty();
    }

    @Test
    void belowMinimumIsIncompatible() {
        var issues = service.validatePlatformCompatibility(
                new PackManifest.Compatibility("2.0.0", null)
        );
        assertThat(issues).anyMatch(i -> "PACK_INCOMPATIBLE_DOCUFORGE_VERSION".equals(i.code()));
    }

    @Test
    void aboveExactMaximumIsIncompatible() {
        var issues = service.validatePlatformCompatibility(
                new PackManifest.Compatibility("1.0.0", "1.4.0")
        );
        assertThat(issues).anyMatch(i -> "PACK_INCOMPATIBLE_DOCUFORGE_VERSION".equals(i.code()));
    }

    @Test
    void wrongMajorRangeIsIncompatible() {
        var issues = service.validatePlatformCompatibility(
                new PackManifest.Compatibility("0.1.0", "0.x")
        );
        assertThat(issues).anyMatch(i -> "PACK_INCOMPATIBLE_DOCUFORGE_VERSION".equals(i.code()));
    }

    @Test
    void snapshotPlatformVersionIsNormalized() {
        PackProperties snap = new PackProperties(
                true, 100, 250, 500, 100, 25, 50, 24, "0.1.0-SNAPSHOT", Set.of("json")
        );
        PackCompatibilityService snapService = new PackCompatibilityService(snap);
        var issues = snapService.validatePlatformCompatibility(
                new PackManifest.Compatibility("0.1.0", null)
        );
        assertThat(issues).isEmpty();
        assertThat(snap.normalizedPlatformVersion()).isEqualTo("0.1.0");
    }

    @Test
    void detectsDowngrade() {
        assertThat(service.isDowngrade("1.3.0", "1.2.0")).isTrue();
        assertThat(service.isDowngrade("1.2.0", "1.3.0")).isFalse();
        assertThat(service.isDowngrade("1.2.0", "1.2.0")).isFalse();
        PackValidationIssue issue = service.downgradeNotAllowedIssue();
        assertThat(issue.code()).isEqualTo("PACK_DOWNGRADE_NOT_ALLOWED");
    }

    @Test
    void classifiesUpdateDiff() {
        assertThat(service.classifyUpdate("1.0.0", "2.0.0")).isEqualTo(Semver.VersionDiff.MAJOR);
        assertThat(service.classifyUpdate("1.0.0", "1.1.0")).isEqualTo(Semver.VersionDiff.MINOR);
        assertThat(service.classifyUpdate("1.0.0", "1.0.1")).isEqualTo(Semver.VersionDiff.PATCH);
    }

    @Test
    void detectsUpgrade() {
        assertThat(service.isUpgrade("1.0.0", "1.1.0")).isTrue();
        assertThat(service.isUpgrade("1.1.0", "1.0.0")).isFalse();
        assertThat(service.isUpgrade("1.0.0", "1.0.0")).isFalse();
        assertThat(service.classifyUpdateKind("1.0.0", "1.0.1")).isEqualTo("PATCH");
    }

    @Test
    void overridePlatformVersionForCheck() {
        var issues = service.validatePlatformCompatibility(
                new PackManifest.Compatibility("0.1.0", "0.x"),
                "0.9.0"
        );
        assertThat(issues).isEmpty();
    }
}
