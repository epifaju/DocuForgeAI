package ai.docuforge.template;

import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

public final class DocxTestFixtures {

    private DocxTestFixtures() {
    }

    public static byte[] minimalDocx(String... paragraphTexts) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            if (paragraphTexts.length == 0) {
                document.createParagraph();
            } else {
                for (String text : paragraphTexts) {
                    XWPFParagraph paragraph = document.createParagraph();
                    XWPFRun run = paragraph.createRun();
                    run.setText(text == null ? "" : text);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }
}