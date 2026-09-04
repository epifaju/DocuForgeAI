package ai.docuforge.web;

import ai.docuforge.common.api.ApiResponse;
import ai.docuforge.config.DocuForgeProperties;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PingController {

    private final DocuForgeProperties properties;

    public PingController(DocuForgeProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.ok(Map.of(
                "service", "docuforge-backend",
                "status", "UP",
                "env", properties.appEnv(),
                "timestamp", Instant.now().toString()
        ));
    }
}