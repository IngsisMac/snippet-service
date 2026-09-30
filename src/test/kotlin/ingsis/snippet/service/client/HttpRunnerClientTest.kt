package ingsis.snippet.service.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class HttpRunnerClientTest {
    private lateinit var restClient: RestClient
    private lateinit var mockServer: MockRestServiceServer
    private lateinit var runnerClient: HttpRunnerClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl("http://runner-service:8082")
        mockServer = MockRestServiceServer.bindTo(builder).build()
        restClient = builder.build()
        runnerClient = HttpRunnerClient(restClient)
    }

    @Test
    fun shouldReturnValidWhenRunnerValidatesSnippet() {
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/validate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.content").value("let x: number = 10;"))
            .andExpect(jsonPath("$.version").value("1.1"))
            .andRespond(
                withSuccess(
                    """{"valid":true,"errors":[]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = runnerClient.validate("let x: number = 10;", "1.1")

        assertTrue(response.valid)
        assertTrue(response.errors.isEmpty())
        mockServer.verify()
    }

    @Test
    fun shouldReturnInvalidWithErrorsWhenRunnerRejectsSyntax() {
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/validate"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"valid":false,"errors":["Unexpected token at line 1"]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = runnerClient.validate("let x = ;", "1.1")

        assertFalse(response.valid)
        assertEquals(1, response.errors.size)
        assertEquals("Unexpected token at line 1", response.errors[0])
        mockServer.verify()
    }

    @Test
    fun shouldReturnTestExecutionResultWhenRunnerExecutesTestCase() {
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/test"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.content").value("println(1);"))
            .andExpect(jsonPath("$.expectedOutputs[0]").value("1"))
            .andRespond(
                withSuccess(
                    """{"passed":true,"actualOutputs":["1"],"expectedOutputs":["1"],"errors":[]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response =
            runnerClient.runTest(
                content = "println(1);",
                version = "1.1",
                inputs = emptyList(),
                expectedOutputs = listOf("1"),
            )

        assertTrue(response.passed)
        assertEquals(listOf("1"), response.actualOutputs)
        mockServer.verify()
    }

    @Test
    fun shouldReturnFormattedContentWhenRunnerFormatsSnippet() {
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/format"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.content").value("let x=10;"))
            .andRespond(
                withSuccess(
                    """{"formattedContent":"let x = 10;"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val formatted = runnerClient.format("let x=10;", "1.1")

        assertEquals("let x = 10;", formatted)
        mockServer.verify()
    }

    @Test
    fun shouldReturnLintFindingsWhenRunnerLintsSnippet() {
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/lint"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"findings":[{"message":"Bad naming","line":1,"column":5}],"findingsCount":1}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = runnerClient.lint("let x = 1;", "1.1")

        assertEquals(1, result.findingsCount)
        assertEquals("Bad naming", result.findings[0].message)
        mockServer.verify()
    }
}
