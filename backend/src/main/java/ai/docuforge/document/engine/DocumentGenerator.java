package ai.docuforge.document.engine;

import java.io.InputStream;
import java.util.Map;

/**
 * Document merge engine boundary (PRD §9).
 * Implementation preserves Word formatting and handles placeholders split across runs.
 */
public interface DocumentGenerator {

    /**
     * Merges {@code data} into the template DOCX stream and returns the stamped bytes.
     *
     * @param templateDocx template content
     * @param data flat form values keyed by variable key (e.g. {@code client.firstName})
     */
    byte[] generate(InputStream templateDocx, Map<String, Object> data);
}