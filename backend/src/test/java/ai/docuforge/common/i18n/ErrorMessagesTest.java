package ai.docuforge.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docuforge.config.I18nConfig;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(I18nConfig.class)
class ErrorMessagesTest {

    @Autowired
    private MessageSource messageSource;

    private ErrorMessages errorMessages;

    @BeforeEach
    void setUp() {
        errorMessages = new ErrorMessages(messageSource);
    }

    @Test
    void localizesKeyToFrenchAndPortuguese() {
        assertThat(errorMessages.localize("error.forbidden", Locale.FRENCH)).isEqualTo("Acces refuse.");
        assertThat(errorMessages.localize("error.forbidden", Locale.forLanguageTag("pt")))
                .isEqualTo("Acesso recusado.");
    }

    @Test
    void localizesLegacyFrenchReason() {
        assertThat(errorMessages.localize("Societe introuvable", Locale.forLanguageTag("pt")))
                .isEqualTo("Empresa nao encontrada");
        assertThat(errorMessages.localize("Identifiants invalides", Locale.forLanguageTag("pt")))
                .isEqualTo("Credenciais invalidas");
    }

    @Test
    void unknownReasonPassesThrough() {
        assertThat(errorMessages.localize("Custom raw message", Locale.FRENCH))
                .isEqualTo("Custom raw message");
    }

    @Test
    void localizesFormFieldKeysAndArgs() {
        assertThat(errorMessages.localize("error.form.required", Locale.forLanguageTag("pt")))
                .isEqualTo("Campo obrigatorio.");
        assertThat(errorMessages.localize("error.form.min_length|3", Locale.forLanguageTag("pt")))
                .isEqualTo("Comprimento minimo: 3");
        assertThat(errorMessages.localize("Champ obligatoire.", Locale.forLanguageTag("pt")))
                .isEqualTo("Campo obrigatorio.");
    }
}
