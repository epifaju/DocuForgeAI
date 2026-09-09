package ai.docuforge.form;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docuforge.domain.template.VariableType;
import ai.docuforge.form.dto.FormFieldConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FormFieldConstraintsParserTest {

    private FormFieldConstraintsParser parser;

    @BeforeEach
    void setUp() {
        parser = new FormFieldConstraintsParser(new ObjectMapper());
    }

    @Test
    void malformedJsonYieldsEmptyConstraints() {
        FormFieldConstraints constraints = parser.parse("{not-json");
        assertThat(constraints.minLength()).isNull();
        assertThat(constraints.maxLength()).isNull();
        assertThat(constraints.min()).isNull();
        assertThat(constraints.max()).isNull();
        assertThat(constraints.pattern()).isNull();
        assertThat(constraints.options()).isEmpty();
    }

    @Test
    void parsesStringAndObjectOptions() {
        FormFieldConstraints constraints = parser.parse("""
                {
                  "options": ["A", {"value": "B"}, {"label": "ignored"}]
                }
                """);
        assertThat(constraints.options()).containsExactly("A", "B");
    }

    @Test
    void aiEnabledDefaultsAndOverrides() {
        assertThat(parser.aiEnabled(null, VariableType.TEXT)).isTrue();
        assertThat(parser.aiEnabled(null, VariableType.LONG_TEXT)).isTrue();
        assertThat(parser.aiEnabled(null, VariableType.NUMBER)).isFalse();
        assertThat(parser.aiEnabled("{\"aiEnabled\":false}", VariableType.LONG_TEXT)).isFalse();
        assertThat(parser.aiEnabled("{\"aiEnabled\":true}", VariableType.NUMBER)).isTrue();
    }

    @Test
    void aiModeDefaultsTextToRewriteAndLongTextToGenerate() {
        assertThat(parser.aiMode(null, VariableType.TEXT)).isEqualTo("REWRITE");
        assertThat(parser.aiMode(null, VariableType.LONG_TEXT)).isEqualTo("GENERATE_PARAGRAPH");
        assertThat(parser.aiMode("{\"aiMode\":\"SUMMARIZE\"}", VariableType.TEXT)).isEqualTo("SUMMARIZE");
    }
}
