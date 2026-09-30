package ingsis.snippet.service.controller

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.config.SecurityConfig
import ingsis.snippet.service.controller.dto.CreateTestCaseRequest
import ingsis.snippet.service.controller.dto.TestCaseExecutionResponse
import ingsis.snippet.service.controller.dto.TestCaseResponse
import ingsis.snippet.service.service.TestCaseService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
import java.util.UUID

@WebMvcTest(TestCaseController::class)
@Import(SecurityConfig::class)
class TestCaseControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var testCaseService: TestCaseService

    private lateinit var sampleTestCase: TestCaseResponse
    private val snippetId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        sampleTestCase =
            TestCaseResponse(
                id = UUID.randomUUID(),
                snippetId = snippetId,
                name = "Basic test",
                inputs = listOf("1"),
                expectedOutputs = listOf("2"),
                env = null,
            )
    }

    @Test
    fun shouldCreateTestCaseEndpoint() {
        val request =
            CreateTestCaseRequest(
                name = "Basic test",
                inputs = listOf("1"),
                expectedOutputs = listOf("2"),
            )

        whenever(testCaseService.createTestCase(eq(snippetId), any(), any())).thenReturn(sampleTestCase)

        mockMvc
            .perform(
                post("/api/snippets/{snippetId}/test-cases", snippetId)
                    .with(jwt().jwt { it.subject("auth0|owner") })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Basic test"))
            .andExpect(jsonPath("$.snippetId").value(snippetId.toString()))
    }

    @Test
    fun shouldListTestCasesEndpoint() {
        whenever(testCaseService.listTestCases(eq(snippetId), any())).thenReturn(listOf(sampleTestCase))

        mockMvc
            .perform(
                get("/api/snippets/{snippetId}/test-cases", snippetId)
                    .with(jwt().jwt { it.subject("auth0|owner") }),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Basic test"))
    }

    @Test
    fun shouldDeleteTestCaseEndpoint() {
        mockMvc
            .perform(
                delete("/api/snippets/{snippetId}/test-cases/{id}", snippetId, sampleTestCase.id)
                    .with(jwt().jwt { it.subject("auth0|owner") }),
            ).andExpect(status().isNoContent)
    }

    @Test
    fun shouldRunSingleTestCaseEndpoint() {
        val executionResponse =
            TestCaseExecutionResponse(
                testCaseId = sampleTestCase.id,
                name = "Basic test",
                passed = true,
                actualOutputs = listOf("2"),
                expectedOutputs = listOf("2"),
                errors = emptyList(),
            )

        whenever(testCaseService.runTestCase(eq(snippetId), eq(sampleTestCase.id), any()))
            .thenReturn(executionResponse)

        mockMvc
            .perform(
                post("/api/snippets/{snippetId}/test-cases/{id}/run", snippetId, sampleTestCase.id)
                    .with(jwt().jwt { it.subject("auth0|reader") }),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.passed").value(true))
            .andExpect(jsonPath("$.testCaseId").value(sampleTestCase.id.toString()))
    }

    @Test
    fun shouldRunAllTestCasesEndpoint() {
        val executionResponse =
            TestCaseExecutionResponse(
                testCaseId = sampleTestCase.id,
                name = "Basic test",
                passed = true,
                actualOutputs = listOf("2"),
                expectedOutputs = listOf("2"),
                errors = emptyList(),
            )

        whenever(testCaseService.runAllTestCases(eq(snippetId), any()))
            .thenReturn(listOf(executionResponse))

        mockMvc
            .perform(
                post("/api/snippets/{snippetId}/test-cases/run-all", snippetId)
                    .with(jwt().jwt { it.subject("auth0|reader") }),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].passed").value(true))
    }
}
