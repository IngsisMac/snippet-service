package ingsis.snippet.service.controller

import ingsis.snippet.service.config.SecurityConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(FileTypeController::class)
@Import(SecurityConfig::class)
class FileTypeControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun shouldListPrintScriptAsTheOnlySupportedLanguage() {
        mockMvc
            .perform(get("/api/snippets/file-types").with(jwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].language").value("printscript"))
            .andExpect(jsonPath("$[0].extension").value("prs"))
            .andExpect(jsonPath("$[0].version").value("1.1"))
    }

    @Test
    fun shouldRejectUnauthenticatedCatalogRequests() {
        mockMvc
            .perform(get("/api/snippets/file-types"))
            .andExpect(status().isUnauthorized)
    }
}
