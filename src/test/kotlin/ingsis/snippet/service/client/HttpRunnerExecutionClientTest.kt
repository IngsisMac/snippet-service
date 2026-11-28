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

    @Test
    fun shouldThrowBadGatewayWhenStartReturnsEmptyBody() {
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/executions"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

        val ex =
            assertThrows(ResponseStatusException::class.java) {
                client.start("println(1);", "1.1", emptyMap())
            }

        assertEquals(HttpStatus.BAD_GATEWAY, ex.statusCode)
        mockServer.verify()
    }

    @Test
    fun shouldVerifyExecutionExistsAndTranslateNotFound() {
        val executionId = UUID.randomUUID()
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/executions/$executionId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess())

        client.requireExists(executionId)
        mockServer.verify()

        mockServer.reset()
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/executions/$executionId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val ex =
            assertThrows(ResponseStatusException::class.java) {
                client.requireExists(executionId)
            }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
        assertEquals("Execution not found", ex.reason)
        mockServer.verify()
    }

    @Test
    fun shouldStreamExecutionOutput() {
        val executionId = UUID.randomUUID()
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/executions/$executionId/stream"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("data: hi\n\n", MediaType.TEXT_EVENT_STREAM))

        var received = ""
        client.stream(executionId) { stream ->
            received = stream.bufferedReader().readText()
        }

        assertEquals("data: hi\n\n", received)
        mockServer.verify()
    }

    @Test
    fun shouldCancelExecutionAndTranslateTooManyRequestsAndServerError() {
        val executionId = UUID.randomUUID()
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/executions/$executionId"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withSuccess())

        client.cancel(executionId)
        mockServer.verify()

        mockServer.reset()
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/executions"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        val tooMany =
            assertThrows(ResponseStatusException::class.java) {
                client.start("println(1);", "1.1", emptyMap())
            }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, tooMany.statusCode)
        assertEquals("Too many executions in progress", tooMany.reason)

        mockServer.reset()
        mockServer
            .expect(requestTo("http://runner-service:8082/runner/executions/$executionId"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val serverErr =
            assertThrows(ResponseStatusException::class.java) {
                client.cancel(executionId)
            }
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, serverErr.statusCode)
        assertEquals("The runner rejected the execution request", serverErr.reason)
        mockServer.verify()
    }
}
