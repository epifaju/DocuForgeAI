package ai.docuforge.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OllamaAIProviderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returnsContentFromJsonResponse() throws Exception {
        startServer((exchange, attempt) -> {
            byte[] body = "{\"response\":\"bonjour monde\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        OllamaAIProvider provider = provider(0);
        AIResponse response = provider.generate(new AIRequest(AiOperation.REWRITE, "salut", null, null));
        assertThat(response.content()).isEqualTo("bonjour monde");
        assertThat(response.provider()).isEqualTo("ollama");
        assertThat(response.promptVersion()).isEqualTo(PromptCatalog.PROMPT_VERSION);
    }

    @Test
    void emptyResponseThrowsUnavailable() throws Exception {
        startServer((exchange, attempt) -> {
            byte[] body = "{\"response\":\"   \"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        OllamaAIProvider provider = provider(0);
        assertThatThrownBy(() -> provider.generate(new AIRequest(AiOperation.REWRITE, "salut", null, null)))
                .isInstanceOf(AiException.class);
    }

    @Test
    void retriesThenSucceeds() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer((exchange, attempt) -> {
            int n = attempts.incrementAndGet();
            if (n == 1) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            byte[] body = "{\"response\":\"ok after retry\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        OllamaAIProvider provider = provider(1);
        AIResponse response = provider.generate(new AIRequest(AiOperation.REWRITE, "salut", null, null));
        assertThat(response.content()).isEqualTo("ok after retry");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void finalFailureThrowsUnavailable() throws Exception {
        startServer((exchange, attempt) -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        OllamaAIProvider provider = provider(1);
        assertThatThrownBy(() -> provider.generate(new AIRequest(AiOperation.REWRITE, "salut", null, null)))
                .isInstanceOf(AiException.class);
    }

    private OllamaAIProvider provider(int maxRetries) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        AiProperties properties = new AiProperties(true, "ollama", base, "test-model", 5, maxRetries);
        return new OllamaAIProvider(properties, new PromptCatalog(), new ObjectMapper());
    }

    private void startServer(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/api/generate", exchange -> handler.handle(exchange, attempts.incrementAndGet()));
        server.start();
    }

    @FunctionalInterface
    private interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange exchange, int attempt) throws IOException;
    }
}
