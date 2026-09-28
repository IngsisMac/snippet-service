package ingsis.snippet.service.controller

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.config.SecurityConfig
import ingsis.snippet.service.controller.dto.CreateTestCaseRequest
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

        whenever(testCaseService.createTestCase(eq(snippetId), any())).thenReturn(sampleTestCase)

        mockMvc
            .perform(
                post("/api/snippets/{snippetId}/test-cases", snippetId)
                    .with(jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Basic test"))
            .andExpect(jsonPath("$.snippetId").value(snippetId.toString()))
    }

    @Test
    fun shouldListTestCasesEndpoint() {
        whenever(testCaseService.listTestCases(snippetId)).thenReturn(listOf(sampleTestCase))

        mockMvc
            .perform(
                get("/api/snippets/{snippetId}/test-cases", snippetId)
                    .with(jwt()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Basic test"))
    }

    @Test
    fun shouldDeleteTestCaseEndpoint() {
        mockMvc
            .perform(
                delete("/api/snippets/{snippetId}/test-cases/{id}", snippetId, sampleTestCase.id)
                    .with(jwt()),
            ).andExpect(status().isNoContent)
    }
}
