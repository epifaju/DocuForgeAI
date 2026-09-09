package ai.docuforge.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ai.docuforge.settings.ApplicationSettingsService;
import ai.docuforge.settings.SettingKeys;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GdprServiceRetentionTest {

    @Mock
    private ApplicationSettingsService applicationSettingsService;

    private GdprService gdprService;
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        gdprService = new GdprService(
                null,
                null,
                null,
                null,
                null,
                null,
                applicationSettingsService,
                null,
                null,
                null
        );
    }

    @Test
    void missingSettingFallsBackToDefault() {
        when(applicationSettingsService.get(companyId, SettingKeys.DATA_RETENTION_DAYS))
                .thenReturn(Optional.empty());

        assertThat(gdprService.retentionDays(companyId)).isEqualTo(GdprService.DEFAULT_RETENTION_DAYS);
    }

    @Test
    void invalidNumberFallsBackToDefault() {
        when(applicationSettingsService.get(companyId, SettingKeys.DATA_RETENTION_DAYS))
                .thenReturn(Optional.of("not-a-number"));

        assertThat(gdprService.retentionDays(companyId)).isEqualTo(GdprService.DEFAULT_RETENTION_DAYS);
    }

    @Test
    void zeroOrNegativeFallsBackToDefault() {
        when(applicationSettingsService.get(companyId, SettingKeys.DATA_RETENTION_DAYS))
                .thenReturn(Optional.of("0"));
        assertThat(gdprService.retentionDays(companyId)).isEqualTo(GdprService.DEFAULT_RETENTION_DAYS);

        when(applicationSettingsService.get(companyId, SettingKeys.DATA_RETENTION_DAYS))
                .thenReturn(Optional.of("-5"));
        assertThat(gdprService.retentionDays(companyId)).isEqualTo(GdprService.DEFAULT_RETENTION_DAYS);
    }

    @Test
    void validPositiveDaysAreReturned() {
        when(applicationSettingsService.get(companyId, SettingKeys.DATA_RETENTION_DAYS))
                .thenReturn(Optional.of(" 90 "));

        assertThat(gdprService.retentionDays(companyId)).isEqualTo(90);
    }
}
