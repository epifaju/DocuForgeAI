package ai.docuforge.document.engine;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docuforge.template.DocxTestFixtures;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

class PoiDocxDocumentGeneratorTest {

    private final PoiDocxDocumentGenerator generator = new PoiDocxDocumentGenerator();

    @Test
    void replacesPlaceholdersIncludingSplitRuns() throws Exception {
        byte[] template;
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            run(paragraph, "Hello {{cli");
            run(paragraph, "ent.nam");
            run(paragraph, "e}}!");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            document.write(out);
            template = out.toByteArray();
        }

        byte[] stamped = generator.generate(
                new ByteArrayInputStream(template),
                Map.of("client.name", "Dupont")
        );

        try (XWPFDocument result = new XWPFDocument(new ByteArrayInputStream(stamped))) {
            String text = result.getParagraphs().getFirst().getRuns().stream()
                    .map(r -> r.getText(0) == null ? "" : r.getText(0))
                    .reduce("", String::concat);
            assertThat(text).isEqualTo("Hello Dupont!");
            assertThat(text).doesNotContain("{{");
        }
    }

    @Test
    void replacesMultipleVariablesFromFixture() throws Exception {
        byte[] template = DocxTestFixtures.minimalDocx(
                "Bonjour {{client.firstName}}",
                "Email {{client.email}}"
        );
        byte[] stamped = generator.generate(
                new ByteArrayInputStream(template),
                Map.of("client.firstName", "Alice", "client.email", "a@b.com")
        );
        try (XWPFDocument result = new XWPFDocument(new ByteArrayInputStream(stamped))) {
            String all = result.getParagraphs().stream()
                    .map(p -> p.getText())
                    .reduce("", (a, b) -> a + "\n" + b);
            assertThat(all).contains("Bonjour Alice");
            assertThat(all).contains("Email a@b.com");
        }
    }

    @Test
    void replacePlaceholdersEscapesMatcherGroupSafely() {
        String out = PoiDocxDocumentGenerator.replacePlaceholders(
                "x={{a}} y={{b}}",
                Map.of("a", "1$2", "b", "ok")
        );
        assertThat(out).isEqualTo("x=1$2 y=ok");
    }

    private static void run(XWPFParagraph paragraph, String text) {
        XWPFRun run = paragraph.createRun();
        run.setText(text);
    }
}