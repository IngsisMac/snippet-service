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
}
