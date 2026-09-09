package ai.docuforge.form;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void validatesPhoneNumberAndDecimalAndDatetimeAndBoolean() {
        TemplateVariable phone = variable("client.phone", VariableType.PHONE, true, null);
        TemplateVariable qty = variable("item.qty", VariableType.NUMBER, true, null);
        TemplateVariable amount = variable("item.amount", VariableType.DECIMAL, true, null);
        TemplateVariable when = variable("event.at", VariableType.DATETIME, true, null);
        TemplateVariable active = variable("flag.active", VariableType.BOOLEAN, true, null);

        assertThatCode(() -> validator.validateOrThrow(
                List.of(phone, qty, amount, when, active),
                Map.of(
                        "client.phone", "+33 1 23 45 67 89",
                        "item.qty", 3,
                        "item.amount", "12.5",
                        "event.at", "2026-09-04T10:15:30",
                        "flag.active", true
                )
        )).doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validateOrThrow(List.of(phone), Map.of("client.phone", "abc")))
                .isInstanceOf(FormValidationException.class);
        assertThatThrownBy(() -> validator.validateOrThrow(List.of(qty), Map.of("item.qty", "1.5")))
                .isInstanceOf(FormValidationException.class);
        assertThatThrownBy(() -> validator.validateOrThrow(List.of(when), Map.of("event.at", "not-a-date")))
                .isInstanceOf(FormValidationException.class);
        assertThatThrownBy(() -> validator.validateOrThrow(List.of(active), Map.of("flag.active", "maybe")))
                .isInstanceOf(FormValidationException.class);
    }

    @Test
    void validatesSelectAndMultiselectOptions() {
        String config = "{\"options\":[\"A\",\"B\",{\"value\":\"C\"}]}";
        TemplateVariable select = variable("choice", VariableType.SELECT, true, config);
        TemplateVariable multi = variable("tags", VariableType.MULTISELECT, true, config);

        assertThatCode(() -> validator.validateOrThrow(List.of(select), Map.of("choice", "A")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateOrThrow(List.of(select), Map.of("choice", "Z")))
                .isInstanceOf(FormValidationException.class);

        assertThatCode(() -> validator.validateOrThrow(List.of(multi), Map.of("tags", List.of("A", "C"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateOrThrow(List.of(multi), Map.of("tags", List.of("A", "Z"))))
                .isInstanceOf(FormValidationException.class);
    }

    @Test
    void rejectsInvalidPatternMaxLengthAndMinMax() {
        TemplateVariable code = variable(
                "code",
                VariableType.TEXT,
                true,
                "{\"validation\":{\"pattern\":\"^[A-Z]+$\",\"maxLength\":4}}"
        );
        TemplateVariable score = variable(
                "item.quantity",
                VariableType.NUMBER,
                true,
                "{\"validation\":{\"min\":1,\"max\":10}}"
        );

        assertThatThrownBy(() -> validator.validateOrThrow(List.of(code), Map.of("code", "ab")))
                .isInstanceOf(FormValidationException.class);
        assertThatThrownBy(() -> validator.validateOrThrow(List.of(code), Map.of("code", "ABCDE")))
                .isInstanceOf(FormValidationException.class);
        assertThatThrownBy(() -> validator.validateOrThrow(
                List.of(variable("code2", VariableType.TEXT, true, "{\"validation\":{\"pattern\":\"[a-z\"}}")),
                Map.of("code2", "x")
        )).isInstanceOf(FormValidationException.class);

        assertThatThrownBy(() -> validator.validateOrThrow(List.of(score), Map.of("item.quantity", 0)))
                .isInstanceOf(FormValidationException.class);
        assertThatThrownBy(() -> validator.validateOrThrow(List.of(score), Map.of("item.quantity", 11)))
                .isInstanceOf(FormValidationException.class);
        assertThatCode(() -> validator.validateOrThrow(List.of(score), Map.of("item.quantity", 5)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownFieldsAndAllowsBlankOptionalAndNullData() {
        TemplateVariable optional = variable("nickname", VariableType.TEXT, false, null);

        assertThatCode(() -> validator.validateOrThrow(List.of(optional), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateOrThrow(List.of(optional), Map.of("nickname", "  ")))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validateOrThrow(List.of(optional), Map.of("unknown", "x")))
                .isInstanceOf(FormValidationException.class)
                .satisfies(ex -> assertThat(((FormValidationException) ex).getDetails())
                        .extracting(d -> d.field())
                        .contains("unknown"));
    }

    @Test
    void componentForMapsEachVariableType() {
        assertThat(FormSchemaService.componentFor(VariableType.TEXT)).isEqualTo("text");
        assertThat(FormSchemaService.componentFor(VariableType.LONG_TEXT)).isEqualTo("textarea");
        assertThat(FormSchemaService.componentFor(VariableType.NUMBER)).isEqualTo("number");
        assertThat(FormSchemaService.componentFor(VariableType.DECIMAL)).isEqualTo("number");
        assertThat(FormSchemaService.componentFor(VariableType.CURRENCY)).isEqualTo("number");
        assertThat(FormSchemaService.componentFor(VariableType.DATE)).isEqualTo("date");
        assertThat(FormSchemaService.componentFor(VariableType.DATETIME)).isEqualTo("datetime");
        assertThat(FormSchemaService.componentFor(VariableType.BOOLEAN)).isEqualTo("checkbox");
        assertThat(FormSchemaService.componentFor(VariableType.EMAIL)).isEqualTo("email");
        assertThat(FormSchemaService.componentFor(VariableType.PHONE)).isEqualTo("tel");
        assertThat(FormSchemaService.componentFor(VariableType.SELECT)).isEqualTo("select");
        assertThat(FormSchemaService.componentFor(VariableType.MULTISELECT)).isEqualTo("multiselect");
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
