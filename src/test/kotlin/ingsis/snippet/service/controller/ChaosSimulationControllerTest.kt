package ingsis.snippet.service.controller

import ingsis.snippet.service.config.SecurityConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ChaosSimulationController::class)
@Import(SecurityConfig::class)
class ChaosSimulationControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun shouldReturn500WithoutAuthentication() {
        mockMvc
            .perform(get("/api/snippets/simulate-error"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.simulated").value(true))
    }

    @Test
    fun shouldReturn500WithCustomMessage() {
        mockMvc
            .perform(get("/api/snippets/simulate-error").param("message", "Custom alert test"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value("Custom alert test"))
            .andExpect(jsonPath("$.simulated").value(true))
    }
}
