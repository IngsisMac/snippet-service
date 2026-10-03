package ingsis.snippet.service.controller

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.config.SecurityConfig
import ingsis.snippet.service.controller.dto.CreateSnippetRequest
import ingsis.snippet.service.controller.dto.SnippetResponse
import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.service.SnippetService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(SnippetController::class)
@Import(SecurityConfig::class)
class SnippetControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var snippetService: SnippetService

    private lateinit var sampleSnippet: SnippetResponse

    @BeforeEach
    fun setUp() {
        sampleSnippet =
            SnippetResponse(
                id = UUID.randomUUID(),
                name = "Sample",
                ownerId = "auth0|123",
                language = "printscript",
                version = "1.1",
                status = ComplianceStatus.COMPLIANT,
                rulesVersion = 1,
                findingsCount = 0,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
    }

    @Test
    fun shouldCreateSnippetEndpoint() {
        val request =
            CreateSnippetRequest(
                name = "Sample",
                language = "printscript",
                version = "1.1",
                content = "let x: number = 10;",
            )

        whenever(snippetService.createSnippet(eq("auth0|123"), any())).thenReturn(sampleSnippet)

        mockMvc
            .perform(
                post("/api/snippets")
                    .with(jwt().jwt { it.subject("auth0|123") })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(sampleSnippet.id.toString()))
            .andExpect(jsonPath("$.name").value("Sample"))
    }

    @Test
    fun shouldGetSnippetEndpoint() {
        whenever(snippetService.getSnippet(eq(sampleSnippet.id), any())).thenReturn(sampleSnippet)

        mockMvc
            .perform(
                get("/api/snippets/{id}", sampleSnippet.id)
                    .with(jwt()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(sampleSnippet.id.toString()))
            .andExpect(jsonPath("$.status").value("COMPLIANT"))
    }

    @Test
    fun shouldDeleteSnippetEndpoint() {
        mockMvc
            .perform(
                delete("/api/snippets/{id}", sampleSnippet.id)
                    .with(jwt().jwt { it.subject("auth0|123") }),
            ).andExpect(status().isNoContent)
    }

    @Test
    fun shouldGetSnippetContentEndpoint() {
        whenever(snippetService.getSnippetContent(eq(sampleSnippet.id), any()))
            .thenReturn("println(\"hello\");")

        mockMvc
            .perform(
                get("/api/snippets/{id}/content", sampleSnippet.id)
                    .with(jwt()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content").value("println(\"hello\");"))
    }

    @Test
    fun shouldFormatSnippetEndpoint() {
        whenever(snippetService.formatSnippet(eq(sampleSnippet.id), any(), isNull()))
            .thenReturn("let a = 1;")

        mockMvc
            .perform(
                post("/api/snippets/{id}/format", sampleSnippet.id)
                    .with(jwt().jwt { it.subject("auth0|123") }),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.formattedContent").value("let a = 1;"))
    }

    @Test
    fun shouldFormatUnsavedEditorContentWhenBodyCarriesIt() {
        whenever(snippetService.formatSnippet(eq(sampleSnippet.id), any(), eq("let b=2;")))
            .thenReturn("let b = 2;")

        mockMvc
            .perform(
                post("/api/snippets/{id}/format", sampleSnippet.id)
                    .with(jwt().jwt { it.subject("auth0|123") })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"let b=2;"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.formattedContent").value("let b = 2;"))
    }

    @Test
    fun shouldRejectSnippetRequestsWithoutToken() {
        mockMvc
            .perform(get("/api/snippets/{id}", sampleSnippet.id))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun shouldLintSnippetEndpoint() {
        val report =
            ingsis.snippet.service.controller.dto.LintReportResponse(
                snippetId = sampleSnippet.id,
                status = ComplianceStatus.COMPLIANT,
                findings = emptyList(),
                findingsCount = 0,
            )
        whenever(snippetService.lintSnippet(eq(sampleSnippet.id), any()))
            .thenReturn(report)

        mockMvc
            .perform(
                post("/api/snippets/{id}/lint", sampleSnippet.id)
                    .with(jwt().jwt { it.subject("auth0|123") }),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLIANT"))
            .andExpect(jsonPath("$.findingsCount").value(0))
    }

    @Test
    fun shouldListSnippetsWithDefaultParamsEndpoint() {
        val page =
            org.springframework.data.domain
                .PageImpl(listOf(sampleSnippet))
        whenever(
            snippetService.listSnippets(
                userId = eq("auth0|123"),
                scope = eq(ingsis.snippet.service.domain.model.SnippetScope.ALL),
                name = eq(null),
                language = eq(null),
                status = eq(null),
                pageable = any(),
            ),
        ).thenReturn(page)

        mockMvc
            .perform(
                get("/api/snippets")
                    .with(jwt().jwt { it.subject("auth0|123") }),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(sampleSnippet.id.toString()))
            .andExpect(jsonPath("$.content[0].name").value("Sample"))
    }

    @Test
    fun shouldListSnippetsWithCustomFiltersEndpoint() {
        val page =
            org.springframework.data.domain
                .PageImpl(listOf(sampleSnippet))
        whenever(
            snippetService.listSnippets(
                userId = eq("auth0|123"),
                scope = eq(ingsis.snippet.service.domain.model.SnippetScope.OWNED),
                name = eq("Sample"),
                language = eq("printscript"),
                status = eq(ComplianceStatus.COMPLIANT),
                pageable = any(),
            ),
        ).thenReturn(page)

        mockMvc
            .perform(
                get("/api/snippets")
                    .param("scope", "OWNED")
                    .param("name", "Sample")
                    .param("language", "printscript")
                    .param("status", "COMPLIANT")
                    .with(jwt().jwt { it.subject("auth0|123") }),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].name").value("Sample"))
    }
}
