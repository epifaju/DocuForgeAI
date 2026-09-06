package ai.docuforge.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.docuforge.auth.security.JwtAuthenticationFilter;
import ai.docuforge.auth.security.JwtService;
import ai.docuforge.common.i18n.ErrorMessages;
import ai.docuforge.config.DocuForgeProperties;
import ai.docuforge.config.JwtProperties;
import ai.docuforge.config.RateLimitProperties;
import ai.docuforge.config.StorageProperties;
import ai.docuforge.security.ratelimit.RateLimitFilter;
import ai.docuforge.storage.LocalStorageProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = PingController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class PingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocuForgeProperties properties;

    @MockitoBean
    private ErrorMessages errorMessages;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private StorageProperties storageProperties;

    @MockitoBean
    private LocalStorageProvider localStorageProvider;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @MockitoBean
    private RateLimitFilter rateLimitFilter;

    @Test
    void pingReturnsOk() throws Exception {
        Mockito.when(properties.appEnv()).thenReturn("test");

        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.service").value("docuforge-backend"));
    }
}