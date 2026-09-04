package ai.docuforge.batch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CsvBatchParser {

    public record CsvRow(int rowNumber, Map<String, String> columns) {
    }

    public List<CsvRow> parse(byte[] csvBytes, int maxRows) {
        if (csvBytes == null || csvBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier CSV vide.");
        }
        try {
            String content = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
            if (content.startsWith("\uFEFF")) {
                content = content.substring(1);
            }
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .setIgnoreSurroundingSpaces(true)
                    .build();
            try (CSVParser parser = CSVParser.parse(content, format)) {
                List<String> headers = parser.getHeaderNames();
                if (headers == null || headers.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV sans en-tetes.");
                }
                List<CsvRow> rows = new ArrayList<>();
                int rowNumber = 1;
                for (CSVRecord record : parser) {
                    rowNumber++;
                    if (rows.size() >= maxRows) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "CSV depasse la limite de " + maxRows + " lignes."
                        );
                    }
                    Map<String, String> columns = new LinkedHashMap<>();
                    for (String header : headers) {
                        columns.put(header, record.isMapped(header) ? record.get(header) : "");
                    }
                    rows.add(new CsvRow(rowNumber, columns));
                }
                if (rows.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV sans lignes de donnees.");
                }
                return rows;
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV illisible.", ex);
        }
    }

    public static Map<String, Object> applyMapping(Map<String, String> csvColumns, Map<String, String> mapping) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (mapping == null || mapping.isEmpty()) {
            csvColumns.forEach((k, v) -> data.put(k, v == null ? "" : v));
            return data;
        }
        mapping.forEach((csvHeader, variableKey) -> {
            if (variableKey == null || variableKey.isBlank()) {
                return;
            }
            String value = csvColumns.getOrDefault(csvHeader, "");
            data.put(variableKey, value == null ? "" : value);
        });
        return data;
    }
}
