package ai.docuforge.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptCatalogTest {

    private final PromptCatalog catalog = new PromptCatalog();

    @Test
    void rendersAllFourOperations() {
        for (AiOperation operation : AiOperation.values()) {
            String rendered = catalog.render(operation, "texte source", null, null);
            assertThat(rendered).isNotBlank();
            assertThat(rendered).contains("texte source");
        }
        assertThat(catalog.promptVersion()).isEqualTo(PromptCatalog.PROMPT_VERSION);
    }

    @Test
    void omitsOptionalBlocksWhenBlankAndIncludesWhenPresent() {
        String without = catalog.render(AiOperation.REWRITE, "bonjour", null, "  ");
        assertThat(without).doesNotContain("Consigne");
        assertThat(without).doesNotContain("Contexte");

        String with = catalog.render(AiOperation.REWRITE, "bonjour", "sois formel", "contrat");
        assertThat(with).contains("sois formel");
        assertThat(with).contains("contrat");
    }
}
