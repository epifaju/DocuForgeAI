package ai.docuforge.ai;

import ai.docuforge.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Ollama HTTP implementation (PRD §11 / §38). Never used when AI_ENABLED=false.
 */
@Component
@ConditionalOnProperty(prefix = "docuforge.ai", name = "enabled", havingValue = "true")
@ConditionalOnMissingBean(AIProvider.class)
public class OllamaAIProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaAIProvider.class);

    private final AiProperties properties;
    private final PromptCatalog promptCatalog;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OllamaAIProvider(AiProperties properties, PromptCatalog promptCatalog, ObjectMapper objectMapper) {
        this.properties = properties;
        this.promptCatalog = promptCatalog;
        this.objectMapper = objectMapper;
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.timeoutSeconds()));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(timeout)
                        .withReadTimeout(timeout)
        );
        String base = properties.ollamaBaseUrl() == null ? "http://127.0.0.1:11434" : properties.ollamaBaseUrl();
        this.restClient = RestClient.builder()
                .baseUrl(base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                .requestFactory(requestFactory)
                .build();
        log.info("Ollama AI provider ready at {} model={}", base, properties.ollamaModel());
    }

    @Override
    public AIResponse generate(AIRequest request) {
        String prompt = promptCatalog.render(
                request.operation(),
                request.text(),
                request.instruction(),
                request.context()
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.ollamaModel());
        body.put("prompt", prompt);
        body.put("stream", false);

        int attempts = Math.max(1, properties.maxRetries() + 1);
        RestClientException last = null;
        long started = System.currentTimeMillis();
        for (int i = 1; i <= attempts; i++) {
            try {
                String raw = restClient.post()
                        .uri("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                String content = extractResponse(raw);
                if (content == null || content.isBlank()) {
                    throw AiException.unavailable("Reponse Ollama vide.", null);
                }
                return new AIResponse(
                        content.trim(),
                        "ollama",
                        properties.ollamaModel(),
                        promptCatalog.promptVersion(),
                        System.currentTimeMillis() - started
                );
            } catch (RestClientException ex) {
                last = ex;
                log.warn("Ollama tentative {}/{} echouee: {}", i, attempts, ex.getMessage());
            }
        }
        throw AiException.unavailable(
                "IA indisponible (Ollama). La generation documentaire reste disponible.",
                last
        );
    }

    private String extractResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.hasNonNull("response")) {
                return node.get("response").asText();
            }
            if (node.has("message") && node.get("message").hasNonNull("content")) {
                return node.get("message").get("content").asText();
            }
            return raw;
        } catch (Exception ex) {
            return raw;
        }
    }
}
