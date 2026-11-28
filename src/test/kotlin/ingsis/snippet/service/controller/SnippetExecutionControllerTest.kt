package ingsis.snippet.service.controller

import ingsis.snippet.service.config.SecurityConfig
import ingsis.snippet.service.service.SnippetExecutionService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID

@WebMvcTest(SnippetExecutionController::class)
@Import(SecurityConfig::class)
class SnippetExecutionControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var executionService: SnippetExecutionService

    @Test
    fun shouldStartExecutionWithEnvAndWithoutBody() {
        val snippetId = UUID.randomUUID()
        val executionId = UUID.randomUUID()
        whenever(executionService.start(snippetId, "auth0|123", mapOf("KEY" to "VAL"))).thenReturn(executionId)
        whenever(executionService.start(snippetId, "auth0|123", emptyMap())).thenReturn(executionId)

        mockMvc
            .perform(
                post("/api/snippets/$snippetId/executions")
                    .with(jwt().jwt { it.subject("auth0|123") })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"env":{"KEY":"VAL"}}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.executionId").value(executionId.toString()))

        mockMvc
            .perform(
                post("/api/snippets/$snippetId/executions")
                    .with(jwt().jwt { it.subject("auth0|123") }),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.executionId").value(executionId.toString()))
    }

    @Test
    fun shouldStreamExecutionOutputWithSseHeaders() {
        val snippetId = UUID.randomUUID()
        val executionId = UUID.randomUUID()
        whenever(executionService.stream(eq(snippetId), eq("auth0|123"), eq(executionId), any())).thenAnswer {
            val sink = it.getArgument<(InputStream) -> Unit>(3)
            sink(ByteArrayInputStream("event: output\ndata: hello\n\n".toByteArray()))
        }

        val mvcResult =
            mockMvc
                .perform(
                    get("/api/snippets/$snippetId/executions/$executionId/stream")
                        .with(jwt().jwt { it.subject("auth0|123") }),
                ).andExpect(request().asyncStarted())
                .andReturn()

        mockMvc
            .perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
            .andExpect(header().string("X-Accel-Buffering", "no"))
            .andExpect(content().string("event: output\ndata: hello\n\n"))
    }

    @Test
    fun shouldSubmitInputAndCancelExecution() {
        val snippetId = UUID.randomUUID()
        val executionId = UUID.randomUUID()

        mockMvc
            .perform(
                post("/api/snippets/$snippetId/executions/$executionId/input")
                    .with(jwt().jwt { it.subject("auth0|123") })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"value":"input-1"}"""),
            ).andExpect(status().isAccepted)

        mockMvc
            .perform(
                delete("/api/snippets/$snippetId/executions/$executionId")
                    .with(jwt().jwt { it.subject("auth0|123") }),
            ).andExpect(status().isNoContent)

        verify(executionService).sendInput(snippetId, "auth0|123", executionId, "input-1")
        verify(executionService).cancel(snippetId, "auth0|123", executionId)
    }
}
