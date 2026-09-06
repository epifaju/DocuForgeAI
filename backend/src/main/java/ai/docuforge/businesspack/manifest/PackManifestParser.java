package ai.docuforge.businesspack.manifest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * JSON → {@link PackManifest} (PRD §64). Does not run schema/semantic validation.
 */
@Component
public class PackManifestParser {

    private final ObjectMapper objectMapper;

    public PackManifestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode readTree(String json) {
        if (!StringUtils.hasText(json)) {
            throw missing();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || node.isNull() || !node.isObject()) {
                throw invalidJson(null);
            }
            return node;
        } catch (PackManifestException ex) {
            throw ex;
        } catch (JsonProcessingException ex) {
            throw invalidJson(ex);
        } catch (IOException ex) {
            throw invalidJson(ex);
        }
    }

    public JsonNode readTree(byte[] utf8Json) {
        if (utf8Json == null || utf8Json.length == 0) {
            throw missing();
        }
        return readTree(new String(utf8Json, StandardCharsets.UTF_8));
    }

    public JsonNode readTree(InputStream in) {
        if (in == null) {
            throw missing();
        }
        try {
            JsonNode node = objectMapper.readTree(in);
            if (node == null || node.isNull() || !node.isObject()) {
                throw invalidJson(null);
            }
            return node;
        } catch (PackManifestException ex) {
            throw ex;
        } catch (IOException ex) {
            throw invalidJson(ex);
        }
    }

    public PackManifest toManifest(JsonNode tree) {
        if (tree == null || !tree.isObject()) {
            throw invalidJson(null);
        }
        try {
            return objectMapper.treeToValue(tree, PackManifest.class);
        } catch (JsonProcessingException ex) {
            throw new PackManifestException(
                    "PACK_MANIFEST_SCHEMA_INVALID",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "error.pack.manifest_schema_invalid",
                    ex
            );
        }
    }

    public PackManifest parse(String json) {
        return toManifest(readTree(json));
    }

    private static PackManifestException missing() {
        return new PackManifestException(
                "PACK_MANIFEST_MISSING",
                HttpStatus.BAD_REQUEST,
                "error.pack.manifest_missing"
        );
    }

    private static PackManifestException invalidJson(Throwable cause) {
        return new PackManifestException(
                "PACK_MANIFEST_INVALID_JSON",
                HttpStatus.BAD_REQUEST,
                "error.pack.manifest_invalid_json",
                cause
        );
    }
}
