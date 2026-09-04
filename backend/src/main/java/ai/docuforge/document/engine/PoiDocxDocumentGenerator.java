package ai.docuforge.document.engine;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * POI-based merger for Phase 7 {@code {{variable}}} placeholders.
 * <p>
 * PRD §9 recommends docx-stamper for split-run safety; classic stamper uses {@code ${SpEL}} and
 * modern office-stamper is docx4j-based, conflicting with our POI parser and {{}} authoring.
 * This implementation coalesces paragraph runs before replacement — same split-run goal —
 * while staying compatible with detected variable keys.
 */
@Component
public class PoiDocxDocumentGenerator implements DocumentGenerator {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*\\}\\}");

    @Override
    public byte[] generate(InputStream templateDocx, Map<String, Object> data) {
        Map<String, Object> safeData = data == null ? Map.of() : data;
        try (OPCPackage pkg = OPCPackage.open(templateDocx);
             XWPFDocument document = new XWPFDocument(pkg);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            walkBody(document, safeData);
            for (XWPFHeader header : document.getHeaderList()) {
                walkBody(header, safeData);
            }
            for (XWPFFooter footer : document.getFooterList()) {
                walkBody(footer, safeData);
            }
            document.write(out);
            return out.toByteArray();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Echec de fusion du document DOCX.",
                    ex
            );
        }
    }

    private void walkBody(IBody body, Map<String, Object> data) {
        for (IBodyElement element : body.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                replaceInParagraph(paragraph, data);
            } else if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        walkBody(cell, data);
                    }
                }
            }
        }
    }

    private void replaceInParagraph(XWPFParagraph paragraph, Map<String, Object> data) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) {
            return;
        }
        StringBuilder original = new StringBuilder();
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            if (text != null) {
                original.append(text);
            }
        }
        String source = original.toString();
        if (!source.contains("{{")) {
            return;
        }
        String replaced = replacePlaceholders(source, data);
        if (replaced.equals(source)) {
            return;
        }
        // Keep formatting of the first run; clear the rest (handles Word-split placeholders).
        XWPFRun first = runs.getFirst();
        first.setText(replaced, 0);
        for (int i = 1; i < runs.size(); i++) {
            runs.get(i).setText("", 0);
        }
    }

    static String replacePlaceholders(String source, Map<String, Object> data) {
        Matcher matcher = PLACEHOLDER.matcher(source);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            Object value = data.get(key);
            String replacement = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}