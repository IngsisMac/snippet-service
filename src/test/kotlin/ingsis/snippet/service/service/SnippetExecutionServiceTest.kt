package ingsis.snippet.service.service

import ingsis.snippet.service.client.RunnerExecutionClient
import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.store.SnippetStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Optional
import java.util.UUID

class SnippetExecutionServiceTest {
    private lateinit var snippetRepository: SnippetRepository
    private lateinit var snippetStore: SnippetStore
    private lateinit var access: SnippetAccess
    private lateinit var executionClient: RunnerExecutionClient
    private lateinit var service: SnippetExecutionService

    @BeforeEach
    fun setUp() {
        snippetRepository = mock()
        snippetStore = mock()
        access = mock()
        executionClient = mock()
        service = SnippetExecutionService(snippetRepository, snippetStore, access, executionClient)
    }

    @Test
    fun shouldStartExecutionWhenSnippetAndContentExist() {
        val snippetId = UUID.randomUUID()
        val executionId = UUID.randomUUID()
        val snippet =
            Snippet(
                id = snippetId,
                name = "Test",
                ownerId = "auth0|123",
                language = "printscript",
                version = "1.1",
            )
        whenever(access.requireView(snippetId, "auth0|123")).thenReturn(PermissionLevel.OWNER)
        whenever(snippetRepository.findById(snippetId)).thenReturn(Optional.of(snippet))
        whenever(snippetStore.get(snippetId)).thenReturn("println(1);")
        whenever(executionClient.start("println(1);", "1.1", mapOf("A" to "1"))).thenReturn(executionId)

        val result = service.start(snippetId, "auth0|123", mapOf("A" to "1"))

        assertEquals(executionId, result)
    }

    @Test
    fun shouldThrowNotFoundWhenSnippetDoesNotExistOnStart() {
        val snippetId = UUID.randomUUID()
        whenever(access.requireView(snippetId, "auth0|123")).thenReturn(PermissionLevel.READ)
        whenever(snippetRepository.findById(snippetId)).thenReturn(Optional.empty())

        val ex =
            assertThrows<ResponseStatusException> {
                service.start(snippetId, "auth0|123", emptyMap())
            }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun shouldThrowNotFoundWhenSnippetContentMissingOnStart() {
        val snippetId = UUID.randomUUID()
        val snippet =
            Snippet(
                id = snippetId,
                name = "Test",
                ownerId = "auth0|123",
                language = "printscript",
                version = "1.1",
            )
        whenever(access.requireView(snippetId, "auth0|123")).thenReturn(PermissionLevel.READ)
        whenever(snippetRepository.findById(snippetId)).thenReturn(Optional.of(snippet))
        whenever(snippetStore.get(snippetId)).thenReturn(null)

        val ex =
            assertThrows<ResponseStatusException> {
                service.start(snippetId, "auth0|123", emptyMap())
            }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun shouldVerifyAccessAndExistenceBeforeStreaming() {
        val snippetId = UUID.randomUUID()
        val executionId = UUID.randomUUID()
        val sink: (InputStream) -> Unit = { it.readBytes() }
        whenever(access.requireView(snippetId, "auth0|123")).thenReturn(PermissionLevel.READ)
        whenever(executionClient.stream(eq(executionId), any())).thenAnswer {
            val callback = it.getArgument<(InputStream) -> Unit>(1)
            callback(ByteArrayInputStream("data: ok\n\n".toByteArray()))
        }

        service.stream(snippetId, "auth0|123", executionId, sink)

        verify(access).requireView(snippetId, "auth0|123")
        verify(executionClient).requireExists(executionId)
        verify(executionClient).stream(eq(executionId), any())
    }

    @Test
    fun shouldForwardInputAndCancelAfterCheckingAccess() {
        val snippetId = UUID.randomUUID()
        val executionId = UUID.randomUUID()
        whenever(access.requireView(snippetId, "auth0|123")).thenReturn(PermissionLevel.READ)

        service.sendInput(snippetId, "auth0|123", executionId, "42")
        service.cancel(snippetId, "auth0|123", executionId)

        verify(executionClient).sendInput(executionId, "42")
        verify(executionClient).cancel(executionId)
    }
}
