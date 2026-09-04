package ai.docuforge.form;

import ai.docuforge.common.api.ErrorResponse.FieldErrorDetail;
import ai.docuforge.domain.template.TemplateVariable;
import ai.docuforge.form.dto.FormFieldConstraints;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Backend validation of dynamic form payloads (PRD §24 / §85).
 * Field messages are {@code error.form.*} keys (optional {@code |arg} suffix) resolved by {@link ai.docuforge.common.i18n.ErrorMessages}.
 */
@Component
public class FormDataValidator {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9][0-9\\s().-]{5,30}$");

    private final FormFieldConstraintsParser constraintsParser;

    public FormDataValidator(FormFieldConstraintsParser constraintsParser) {
        this.constraintsParser = constraintsParser;
    }

    public void validateOrThrow(List<TemplateVariable> variables, Map<String, Object> data) {
        List<FieldErrorDetail> errors = validateCollecting(variables, data);
        if (!errors.isEmpty()) {
            throw new FormValidationException("error.form.invalid", errors);
        }
    }

    public List<FieldErrorDetail> validateCollecting(List<TemplateVariable> variables, Map<String, Object> data) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        Map<String, Object> safeData = data == null ? Map.of() : data;

        for (TemplateVariable variable : variables) {
            Object raw = safeData.get(variable.getVariableKey());
            FormFieldConstraints constraints = constraintsParser.parse(variable.getConfiguration());
            validateField(variable, raw, constraints, errors);
        }

        for (String unknown : safeData.keySet()) {
            boolean known = variables.stream().anyMatch(v -> v.getVariableKey().equals(unknown));
            if (!known) {
                errors.add(new FieldErrorDetail(unknown, "error.form.unknown_field"));
            }
        }
        return errors;
    }

    private void validateField(
            TemplateVariable variable,
            Object raw,
            FormFieldConstraints constraints,
            List<FieldErrorDetail> errors
    ) {
        String key = variable.getVariableKey();
        boolean blank = isBlank(raw);
        if (variable.isRequired() && blank) {
            errors.add(new FieldErrorDetail(key, "error.form.required"));
            return;
        }
        if (blank) {
            return;
        }

        switch (variable.getType()) {
            case TEXT, LONG_TEXT -> validateText(key, stringValue(raw), constraints, errors);
            case EMAIL -> {
                String value = stringValue(raw);
                validateText(key, value, constraints, errors);
                if (!EMAIL.matcher(value).matches()) {
                    errors.add(new FieldErrorDetail(key, "error.form.email"));
                }
            }
            case PHONE -> {
                if (!PHONE.matcher(stringValue(raw)).matches()) {
                    errors.add(new FieldErrorDetail(key, "error.form.phone"));
                }
            }
            case NUMBER -> validateInteger(key, raw, constraints, errors);
            case DECIMAL -> validateDecimal(key, raw, constraints, false, errors);
            case CURRENCY -> validateDecimal(key, raw, constraints, true, errors);
            case DATE -> validateDate(key, stringValue(raw), errors);
            case DATETIME -> validateDateTime(key, stringValue(raw), errors);
            case BOOLEAN -> {
                if (!(raw instanceof Boolean)
                        && !"true".equalsIgnoreCase(stringValue(raw))
                        && !"false".equalsIgnoreCase(stringValue(raw))) {
                    errors.add(new FieldErrorDetail(key, "error.form.boolean"));
                }
            }
            case SELECT -> {
                String value = stringValue(raw);
                if (!constraints.options().isEmpty() && !constraints.options().contains(value)) {
                    errors.add(new FieldErrorDetail(key, "error.form.not_allowed"));
                }
            }
            case MULTISELECT -> validateMultiSelect(key, raw, constraints, errors);
            default -> errors.add(new FieldErrorDetail(key, "error.form.unsupported_type"));
        }
    }

    private void validateText(
            String key,
            String value,
            FormFieldConstraints constraints,
            List<FieldErrorDetail> errors
    ) {
        if (constraints.minLength() != null && value.length() < constraints.minLength()) {
            errors.add(new FieldErrorDetail(key, "error.form.min_length|" + constraints.minLength()));
        }
        if (constraints.maxLength() != null && value.length() > constraints.maxLength()) {
            errors.add(new FieldErrorDetail(key, "error.form.max_length|" + constraints.maxLength()));
        }
        if (constraints.pattern() != null && !constraints.pattern().isBlank()) {
            try {
                if (!Pattern.compile(constraints.pattern()).matcher(value).matches()) {
                    errors.add(new FieldErrorDetail(key, "error.form.format"));
                }
            } catch (Exception ex) {
                errors.add(new FieldErrorDetail(key, "error.form.pattern_invalid"));
            }
        }
    }

    private void validateInteger(
            String key,
            Object raw,
            FormFieldConstraints constraints,
            List<FieldErrorDetail> errors
    ) {
        BigDecimal number;
        try {
            number = toDecimal(raw);
        } catch (NumberFormatException ex) {
            errors.add(new FieldErrorDetail(key, "error.form.number"));
            return;
        }
        if (number.stripTrailingZeros().scale() > 0) {
            errors.add(new FieldErrorDetail(key, "error.form.integer"));
            return;
        }
        applyMinMax(key, number, constraints, errors);
    }

    private void validateDecimal(
            String key,
            Object raw,
            FormFieldConstraints constraints,
            boolean currency,
            List<FieldErrorDetail> errors
    ) {
        BigDecimal number;
        try {
            number = toDecimal(raw);
        } catch (NumberFormatException ex) {
            errors.add(new FieldErrorDetail(key, currency ? "error.form.amount" : "error.form.number"));
            return;
        }
        BigDecimal min = constraints.min();
        if (min == null && currency) {
            min = BigDecimal.ZERO;
        }
        if (min != null && number.compareTo(min) < 0) {
            errors.add(new FieldErrorDetail(
                    key,
                    currency ? "error.form.amount_min|" + min : "error.form.min|" + min
            ));
        }
        if (constraints.max() != null && number.compareTo(constraints.max()) > 0) {
            errors.add(new FieldErrorDetail(key, "error.form.max|" + constraints.max()));
        }
    }

    private void applyMinMax(
            String key,
            BigDecimal number,
            FormFieldConstraints constraints,
            List<FieldErrorDetail> errors
    ) {
        if (constraints.min() != null && number.compareTo(constraints.min()) < 0) {
            errors.add(new FieldErrorDetail(key, "error.form.min|" + constraints.min()));
        }
        if (constraints.max() != null && number.compareTo(constraints.max()) > 0) {
            errors.add(new FieldErrorDetail(key, "error.form.max|" + constraints.max()));
        }
    }

    private void validateDate(String key, String value, List<FieldErrorDetail> errors) {
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            errors.add(new FieldErrorDetail(key, "error.form.date"));
        }
    }

    private void validateDateTime(String key, String value, List<FieldErrorDetail> errors) {
        try {
            Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                LocalDateTime.parse(value);
            } catch (DateTimeParseException ex) {
                errors.add(new FieldErrorDetail(key, "error.form.datetime"));
            }
        }
    }

    private void validateMultiSelect(
            String key,
            Object raw,
            FormFieldConstraints constraints,
            List<FieldErrorDetail> errors
    ) {
        List<String> values = toStringList(raw);
        if (values.isEmpty()) {
            errors.add(new FieldErrorDetail(key, "error.form.multiselect"));
            return;
        }
        if (!constraints.options().isEmpty()) {
            for (String value : values) {
                if (!constraints.options().contains(value)) {
                    errors.add(new FieldErrorDetail(key, "error.form.not_allowed_value|" + value));
                    return;
                }
            }
        }
    }

    private static boolean isBlank(Object raw) {
        if (raw == null) {
            return true;
        }
        if (raw instanceof String s) {
            return s.isBlank();
        }
        if (raw instanceof Collection<?> c) {
            return c.isEmpty();
        }
        return false;
    }

    private static String stringValue(Object raw) {
        return String.valueOf(raw).trim();
    }

    private static BigDecimal toDecimal(Object raw) {
        if (raw instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(stringValue(raw));
    }

    private static List<String> toStringList(Object raw) {
        if (raw instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        if (raw instanceof String s) {
            if (s.isBlank()) {
                return List.of();
            }
            return List.of(s.split("\\s*,\\s*"));
        }
        return List.of(String.valueOf(raw));
    }
}
