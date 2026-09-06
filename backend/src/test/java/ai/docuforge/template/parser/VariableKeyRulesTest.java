package ai.docuforge.template.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VariableKeyRulesTest {

    @Test
    void acceptsDottedKeysIncludingDescriptionLeaf() {
        assertThat(VariableKeyRules.isValid("work.description")).isTrue();
        assertThat(VariableKeyRules.isValid("intervention.description")).isTrue();
        assertThat(VariableKeyRules.isValid("company.name")).isTrue();
    }

    @Test
    void rejectsScriptPathSegmentAndJavascriptUri() {
        assertThat(VariableKeyRules.isValid("payload.script")).isFalse();
        assertThat(VariableKeyRules.isValid("script")).isFalse();
        assertThat(VariableKeyRules.isValid("javascript:alert")).isFalse();
    }
}
