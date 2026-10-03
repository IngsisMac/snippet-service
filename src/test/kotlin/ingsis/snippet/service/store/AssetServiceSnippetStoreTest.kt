package ingsis.snippet.service.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import java.util.UUID

class AssetServiceSnippetStoreTest {
    private lateinit var restClient: RestClient
    private lateinit var mockServer: MockRestServiceServer
    private lateinit var store: AssetServiceSnippetStore

    private val baseUrl = "http://asset-service:8083"

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl(baseUrl)
        mockServer = MockRestServiceServer.bindTo(builder).build()
        restClient = builder.build()
        store = AssetServiceSnippetStore(restClient)
    }

    @Test
    fun shouldSaveSnippetContentSuccessfully() {
        val snippetId = UUID.randomUUID()
        val content = "let x: number = 42;"

        mockServer
            .expect(requestTo("$baseUrl/v1/asset/snippets/$snippetId"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header("Content-Type", MediaType.TEXT_PLAIN_VALUE))
            .andRespond(withStatus(HttpStatus.CREATED))

        store.put(snippetId, content)

        mockServer.verify()
    }

    @Test
    fun shouldGetSnippetContentSuccessfully() {
        val snippetId = UUID.randomUUID()
        val content = "println('hello snippet');"

        mockServer
            .expect(requestTo("$baseUrl/v1/asset/snippets/$snippetId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(content, MediaType.TEXT_PLAIN),
            )

        val result = store.get(snippetId)

        assertEquals(content, result)
        mockServer.verify()
    }

    @Test
    fun shouldReturnNullWhenSnippetContentNotFound() {
        val snippetId = UUID.randomUUID()

        mockServer
            .expect(requestTo("$baseUrl/v1/asset/snippets/$snippetId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val result = store.get(snippetId)

        assertNull(result)
        mockServer.verify()
    }

    @Test
    fun shouldThrowWhenGetFailsWithServerError() {
        val snippetId = UUID.randomUUID()

        mockServer
            .expect(requestTo("$baseUrl/v1/asset/snippets/$snippetId"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThrows<HttpServerErrorException> {
            store.get(snippetId)
        }
        mockServer.verify()
    }

    @Test
    fun shouldDeleteSnippetContentSuccessfully() {
        val snippetId = UUID.randomUUID()

        mockServer
            .expect(requestTo("$baseUrl/v1/asset/snippets/$snippetId"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.NO_CONTENT))

        store.delete(snippetId)

        mockServer.verify()
    }

    @Test
    fun shouldIgnore404WhenDeletingNonExistentSnippetContent() {
        val snippetId = UUID.randomUUID()

        mockServer
            .expect(requestTo("$baseUrl/v1/asset/snippets/$snippetId"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        store.delete(snippetId)

        mockServer.verify()
    }
}
