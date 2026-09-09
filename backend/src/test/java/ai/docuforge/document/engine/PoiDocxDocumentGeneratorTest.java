package ai.docuforge.document.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.template.DocxTestFixtures;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
            ByteArrayOutputStream out = new ByteArrayOutputStream();
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
                    .map(XWPFParagraph::getText)
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

    @Test
    void replacesHeaderFooterAndTablePlaceholders() throws Exception {
        byte[] template;
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFHeader header = document.createHeader(HeaderFooterType.DEFAULT);
            run(header.createParagraph(), "H {{client.name}}");
            XWPFFooter footer = document.createFooter(HeaderFooterType.DEFAULT);
            run(footer.createParagraph(), "F {{client.name}}");
            XWPFTable table = document.createTable(1, 1);
            XWPFTableCell cell = table.getRow(0).getCell(0);
            cell.removeParagraph(0);
            run(cell.addParagraph(), "T {{client.name}}");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            template = out.toByteArray();
        }

        byte[] stamped = generator.generate(
                new ByteArrayInputStream(template),
                Map.of("client.name", "Acme")
        );
        try (XWPFDocument result = new XWPFDocument(new ByteArrayInputStream(stamped))) {
            assertThat(result.getHeaderList().getFirst().getText()).contains("H Acme");
            assertThat(result.getFooterList().getFirst().getText()).contains("F Acme");
            assertThat(result.getTables().getFirst().getRow(0).getCell(0).getText()).contains("T Acme");
        }
    }

    @Test
    void missingKeyAndNullDataBecomeEmptyAndSpacedKeysWork() throws Exception {
        byte[] template = DocxTestFixtures.minimalDocx("Hello {{ spaced.key }} / {{missing}}");
        byte[] stamped = generator.generate(
                new ByteArrayInputStream(template),
                Map.of("spaced.key", "OK")
        );
        try (XWPFDocument result = new XWPFDocument(new ByteArrayInputStream(stamped))) {
            assertThat(result.getParagraphs().getFirst().getText()).isEqualTo("Hello OK / ");
        }

        byte[] withNullData = generator.generate(new ByteArrayInputStream(template), null);
        try (XWPFDocument result = new XWPFDocument(new ByteArrayInputStream(withNullData))) {
            assertThat(result.getParagraphs().getFirst().getText()).isEqualTo("Hello  / ");
        }
    }

    @Test
    void noOpWithoutPlaceholdersAndRejectsCorruptBytes() throws Exception {
        byte[] plain = DocxTestFixtures.minimalDocx("Plain text only");
        byte[] stamped = generator.generate(new ByteArrayInputStream(plain), Map.of("x", "y"));
        try (XWPFDocument result = new XWPFDocument(new ByteArrayInputStream(stamped))) {
            assertThat(result.getParagraphs().getFirst().getText()).contains("Plain text only");
        }

        assertThatThrownBy(() -> generator.generate(new ByteArrayInputStream("not-a-docx".getBytes()), Map.of()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static void run(XWPFParagraph paragraph, String text) {
        XWPFRun run = paragraph.createRun();
        run.setText(text);
    }
}
