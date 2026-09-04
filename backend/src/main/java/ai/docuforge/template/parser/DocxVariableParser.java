package ai.docuforge.template.parser;

import ai.docuforge.domain.template.VariableType;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Extracts {{variable}} placeholders from DOCX content.
 * Paragraph runs are concatenated first so Word-split placeholders are still detected (PRD §9 / §22).
 */
@Component
public class DocxVariableParser {

    private static final Logger log = LoggerFactory.getLogger(DocxVariableParser.class);

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*\\}\\}");

    public List<DetectedVariable> parse(InputStream docxStream) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        try (OPCPackage pkg = OPCPackage.open(docxStream); XWPFDocument document = new XWPFDocument(pkg)) {
            walkBody(document, keys);
            for (XWPFHeader header : document.getHeaderList()) {
                walkBody(header, keys);
            }
            for (XWPFFooter footer : document.getFooterList()) {
                walkBody(footer, keys);
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le fichier DOCX est illisible ou corrompu.",
                    ex
            );
        }

        List<DetectedVariable> detected = new ArrayList<>();
        int order = 0;
        for (String key : keys) {
            if (!VariableKeyRules.isValid(key)) {
                log.warn("Placeholder ignore (cle invalide): {}", key);
                continue;
            }
            detected.add(new DetectedVariable(key, labelFromKey(key), inferType(key), order++));
        }
        return List.copyOf(detected);
    }

    public List<DetectedVariable> parse(byte[] docxBytes) {
        return parse(new java.io.ByteArrayInputStream(docxBytes));
    }

    private void walkBody(IBody body, Set<String> keys) {
        for (IBodyElement element : body.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                extractFromText(paragraphText(paragraph), keys);
            } else if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        walkBody(cell, keys);
                    }
                }
            }
        }
    }

    private static String paragraphText(XWPFParagraph paragraph) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.getText(0);
            if (text != null) {
                sb.append(text);
            }
        }
        return sb.toString();
    }

    private static void extractFromText(String text, Set<String> keys) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            String key = VariableKeyRules.normalize(matcher.group(1));
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
    }

    static String labelFromKey(String key) {
        String[] parts = key.split("\\.");
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                label.append(' ');
            }
            label.append(humanizeSegment(parts[i]));
        }
        return label.toString();
    }

    private static String humanizeSegment(String segment) {
        if (segment.isEmpty()) {
            return segment;
        }
        String spaced = segment.replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    static VariableType inferType(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        String leaf = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : lower;
        if (leaf.contains("email") || leaf.endsWith("mail")) {
            return VariableType.EMAIL;
        }
        if (leaf.contains("phone") || leaf.contains("mobile") || leaf.contains("tel")) {
            return VariableType.PHONE;
        }
        if (leaf.contains("datetime") || leaf.contains("timestamp")) {
            return VariableType.DATETIME;
        }
        if (leaf.contains("date")) {
            return VariableType.DATE;
        }
        if (leaf.contains("amount") || leaf.contains("price") || leaf.contains("total")
                || leaf.contains("currency") || leaf.contains("montant")) {
            return VariableType.CURRENCY;
        }
        if (leaf.startsWith("is") || leaf.startsWith("has") || leaf.contains("enabled")
                || leaf.contains("active") || leaf.equals("boolean")) {
            return VariableType.BOOLEAN;
        }
        if (leaf.contains("description") || leaf.contains("summary") || leaf.contains("notes")
                || leaf.contains("comment")) {
            return VariableType.LONG_TEXT;
        }
        if (leaf.contains("count") || leaf.contains("quantity") || leaf.contains("qty")
                || leaf.endsWith("number") || leaf.endsWith("num")) {
            return VariableType.NUMBER;
        }
        return VariableType.TEXT;
    }
}