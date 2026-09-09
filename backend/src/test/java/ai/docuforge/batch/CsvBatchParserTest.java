package ai.docuforge.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CsvBatchParserTest {

    private final CsvBatchParser parser = new CsvBatchParser();

    @Test
    void rejectsEmptyCsv() {
        assertThatThrownBy(() -> parser.parse(new byte[0], 10))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void stripsBomAndParsesRows() {
        byte[] csv = ("\uFEFFname,email\nAlice,a@b.com\n").getBytes(StandardCharsets.UTF_8);
        List<CsvBatchParser.CsvRow> rows = parser.parse(csv, 10);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().columns()).containsEntry("name", "Alice").containsEntry("email", "a@b.com");
        assertThat(rows.getFirst().rowNumber()).isEqualTo(2);
    }

    @Test
    void rejectsMissingHeadersAndMissingDataRows() {
        assertThatThrownBy(() -> parser.parse("\n".getBytes(StandardCharsets.UTF_8), 10))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> parser.parse("name,email\n".getBytes(StandardCharsets.UTF_8), 10))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("lignes");
    }

    @Test
    void rejectsWhenExceedingMaxRows() {
        String csv = "name\n" + "a\n".repeat(3);
        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8), 2))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("limite");
    }

    @Test
    void applyMappingIdentityAndRemapSkipsBlankKeys() {
        Map<String, String> columns = Map.of("A", "1", "B", "2");
        assertThat(CsvBatchParser.applyMapping(columns, null))
                .containsEntry("A", "1")
                .containsEntry("B", "2");

        assertThat(CsvBatchParser.applyMapping(columns, Map.of("A", "client.name", "B", "")))
                .containsOnlyKeys("client.name")
                .containsEntry("client.name", "1");
    }
}
