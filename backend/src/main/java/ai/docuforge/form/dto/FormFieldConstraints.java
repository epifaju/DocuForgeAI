package ai.docuforge.form.dto;

import java.math.BigDecimal;
import java.util.List;

public record FormFieldConstraints(
        Integer minLength,
        Integer maxLength,
        BigDecimal min,
        BigDecimal max,
        String pattern,
        List<String> options
) {
}