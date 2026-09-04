package ai.docuforge.template.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.domain.template.VariableType;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DocxVariableParserTest {

    private final DocxVariableParser parser = new DocxVariableParser();

    @Test
    void detectsVariablesAcrossSplitRunsAndTables() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            // Word-style split of {{client.firstName}}
            run(paragraph, "{{cli");
            run(paragraph, "ent.fir");
            run(paragraph, "stName}}");
            run(paragraph, " et ");
            run(paragraph, "{{case.reference}}");

            XWPFTable table = document.createTable(1, 1);
            XWPFTableRow row = table.getRow(0);
            XWPFParagraph cellParagraph = row.getCell(0).getParagraphs().getFirst();
            run(cellParagraph, "Email: {{client.email}}");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            docx = out.toByteArray();
        }

        List<DetectedVariable> detected = parser.parse(docx);
        assertThat(detected).extracting(DetectedVariable::key)
                .containsExactly("client.firstName", "case.reference", "client.email");
        assertThat(detected.get(2).type()).isEqualTo(VariableType.EMAIL);
        assertThat(detected.get(0).label()).isEqualTo("Client First Name");
    }

    @Test
    void skipsInvalidKeysAndDeduplicates() throws Exception {
        byte[] docx = docxWithText("Hello {{client.name}} {{../evil}} {{client.name}} {{}}");
        List<DetectedVariable> detected = parser.parse(docx);
        assertThat(detected).extracting(DetectedVariable::key).containsExactly("client.name");
    }

    @Test
    void rejectsCorruptPayload() {
        byte[] bogus = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x00, 0x01, 0x02};
        assertThatThrownBy(() -> parser.parse(bogus))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void infersCommonTypes() {
        assertThat(DocxVariableParser.inferType("client.email")).isEqualTo(VariableType.EMAIL);
        assertThat(DocxVariableParser.inferType("order.date")).isEqualTo(VariableType.DATE);
        assertThat(DocxVariableParser.inferType("invoice.total")).isEqualTo(VariableType.CURRENCY);
        assertThat(DocxVariableParser.inferType("user.isActive")).isEqualTo(VariableType.BOOLEAN);
        assertThat(DocxVariableParser.inferType("notes.description")).isEqualTo(VariableType.LONG_TEXT);
    }

    private static byte[] docxWithText(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            run(paragraph, text);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    private static void run(XWPFParagraph paragraph, String text) {
        XWPFRun run = paragraph.createRun();
        run.setText(text);
    }
}