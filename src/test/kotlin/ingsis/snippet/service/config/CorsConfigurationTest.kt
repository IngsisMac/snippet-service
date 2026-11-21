package ingsis.snippet.service.config

import ingsis.snippet.service.controller.FileTypeController
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Verifica la politica de CORS declarada en [SecurityConfig]: el preflight de una origin
 * permitida se responde sin token, y el de una origin desconocida se rechaza.
 */
@WebMvcTest(FileTypeController::class)
@Import(SecurityConfig::class)
class CorsConfigurationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var allowedOrigin: String
    private lateinit var unknownOrigin: String

    @BeforeEach
    fun setUp() {
        allowedOrigin = "http://localhost:5173"
        unknownOrigin = "https://sitio-no-autorizado.example.com"
    }

    @Test
    @DisplayName("El preflight de una origin permitida se responde sin exigir token")
    fun shouldAnswerThePreflightOfAnAllowedOriginWithoutRequiringAToken() {
        mockMvc
            .perform(
                options("/api/snippets/file-types")
                    .header(HttpHeaders.ORIGIN, allowedOrigin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION),
            ).andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin))
            .andExpect(
                header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("GET")),
            ).andExpect(
                header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    containsString(HttpHeaders.AUTHORIZATION),
                ),
            ).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "1800"))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
    }

    @Test
    @DisplayName("El preflight de una origin desconocida se rechaza y no devuelve headers de CORS")
    fun shouldRejectThePreflightOfAnUnknownOrigin() {
        mockMvc
            .perform(
                options("/api/snippets/file-types")
                    .header(HttpHeaders.ORIGIN, unknownOrigin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"),
            ).andExpect(status().isForbidden)
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    @Test
    @DisplayName("La respuesta real lleva la origin permitida y expone el header de correlacion")
    fun shouldTagTheActualResponseWithTheAllowedOriginAndExposeTheCorrelationHeader() {
        mockMvc
            .perform(
                get("/api/snippets/file-types")
                    .header(HttpHeaders.ORIGIN, allowedOrigin)
                    .with(jwt()),
            ).andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin))
            .andExpect(
                header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("X-Correlation-Id")),
            )
    }
}
