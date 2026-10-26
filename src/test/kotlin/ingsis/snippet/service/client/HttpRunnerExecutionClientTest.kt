package ingsis.snippet.service.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class HttpRunnerExecutionClientTest {
    private lateinit var restClient: RestClient
    private lateinit var streamingClient: RestClient
    private lateinit var mockServer: MockRestServiceServer
    private lateinit var client: HttpRunnerExecutionClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl("http://runner-service:8082")
        mockServer = MockRestServiceServer.bindTo(builder).build()
        restClient = builder.build()
        streamingClient = builder.build()
        client = HttpRunnerExecutionClient(restClient, streamingClient)
    }

    @Test
    fun shouldStartExecutionSuccessfully() {
        val executionId = UUID.randomUUID()
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/executions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.content").value("println(1);"))
            .andExpect(jsonPath("$.version").value("1.1"))
            .andRespond(
                withSuccess(
                    """{"executionId":"$executionId"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = client.start("println(1);", "1.1", emptyMap())
        assertEquals(executionId, result)
        mockServer.verify()
    }

    @Test
    fun shouldSendInputSuccessfully() {
        val executionId = UUID.randomUUID()
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/executions/$executionId/input"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.value").value("hello"))
            .andRespond(withSuccess())

        client.sendInput(executionId, "hello")
        mockServer.verify()
    }

    @Test
    fun shouldTranslateConflictWhenInputSubmittedToFinishedSession() {
        val executionId = UUID.randomUUID()
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/executions/$executionId/input"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.CONFLICT))

        val ex =
            assertThrows(ResponseStatusException::class.java) {
                client.sendInput(executionId, "late")
            }

        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        assertEquals("Execution already finished", ex.reason)
        mockServer.verify()
    }
}
