package ai.docuforge.form;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.domain.template.TemplateVariable;
import ai.docuforge.domain.template.VariableType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FormDataValidatorTest {

    private FormDataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FormDataValidator(new FormFieldConstraintsParser(new ObjectMapper()));
    }

    @Test
    void acceptsValidPayload() {
        TemplateVariable name = variable("client.name", VariableType.TEXT, true, null);
        TemplateVariable email = variable("client.email", VariableType.EMAIL, true, null);
        TemplateVariable total = variable("invoice.total", VariableType.CURRENCY, true, null);
        TemplateVariable date = variable("document.date", VariableType.DATE, true, null);

        assertThatCode(() -> validator.validateOrThrow(
                List.of(name, email, total, date),
                Map.of(
                        "client.name", "Dupont",
                        "client.email", "a@b.com",
                        "invoice.total", "12.50",
                        "document.date", "2026-09-04"
                )
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingRequiredAndInvalidEmailAndNegativeCurrency() {
        TemplateVariable name = variable("client.name", VariableType.TEXT, true, null);
        TemplateVariable email = variable("client.email", VariableType.EMAIL, true, null);
        TemplateVariable total = variable("invoice.total", VariableType.CURRENCY, true, null);

        assertThatThrownBy(() -> validator.validateOrThrow(
                List.of(name, email, total),
                Map.of(
                        "client.email", "not-an-email",
                        "invoice.total", -1
                )
        ))
                .isInstanceOf(FormValidationException.class)
                .satisfies(ex -> {
                    FormValidationException fex = (FormValidationException) ex;
                    assertThatCode(() -> {}).doesNotThrowAnyException();
                    org.assertj.core.api.Assertions.assertThat(fex.getDetails())
                            .extracting(d -> d.field())
                            .contains("client.name", "client.email", "invoice.total");
                });
    }

    @Test
    void enforcesMinLengthFromConfiguration() {
        TemplateVariable name = variable(
                "client.name",
                VariableType.TEXT,
                true,
                "{\"validation\":{\"minLength\":3,\"maxLength\":10}}"
        );
        assertThatThrownBy(() -> validator.validateOrThrow(List.of(name), Map.of("client.name", "ab")))
                .isInstanceOf(FormValidationException.class);
        assertThatCode(() -> validator.validateOrThrow(List.of(name), Map.of("client.name", "abcd")))
                .doesNotThrowAnyException();
    }

    private static TemplateVariable variable(String key, VariableType type, boolean required, String config) {
        TemplateVariable variable = new TemplateVariable();
        variable.setVariableKey(key);
        variable.setLabel(key);
        variable.setType(type);
        variable.setRequired(required);
        variable.setDisplayOrder(0);
        variable.setConfiguration(config);
        return variable;
    }
}