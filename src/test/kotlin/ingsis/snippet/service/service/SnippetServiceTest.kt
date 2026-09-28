package ingsis.snippet.service.service

import ingsis.snippet.service.controller.dto.CreateSnippetRequest
import ingsis.snippet.service.controller.dto.UpdateSnippetRequest
import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class SnippetServiceTest {
    private lateinit var snippetRepository: SnippetRepository
    private lateinit var statusRepository: SnippetStatusRepository
    private lateinit var snippetService: SnippetService

    @BeforeEach
    fun setUp() {
        snippetRepository = mock()
        statusRepository = mock()
        snippetService = SnippetService(snippetRepository, statusRepository)
    }

    @Test
    fun shouldCreateSnippetSuccessfully() {
        val ownerId = "auth0|user123"
        val request =
            CreateSnippetRequest(
                name = "Test Snippet",
                language = "printscript",
                version = "1.1",
                content = "let a: number = 5;",
            )

        val snippet =
            Snippet(
                name = request.name,
                ownerId = ownerId,
                language = request.language,
                version = request.version,
            )
        val status = SnippetStatus(snippetId = snippet.id)

        whenever(snippetRepository.save(any<Snippet>())).thenReturn(snippet)
        whenever(statusRepository.save(any<SnippetStatus>())).thenReturn(status)

        val response = snippetService.createSnippet(ownerId, request)

        assertNotNull(response)
        assertEquals("Test Snippet", response.name)
        assertEquals(ownerId, response.ownerId)
        assertEquals(ComplianceStatus.PENDING, response.status)
        verify(snippetRepository).save(any<Snippet>())
        verify(statusRepository).save(any<SnippetStatus>())
    }

    @Test
    fun shouldGetSnippetById() {
        val id = UUID.randomUUID()
        val snippet =
            Snippet(
                id = id,
                name = "Existing Snippet",
                ownerId = "auth0|user123",
                language = "printscript",
                version = "1.1",
            )
        val status =
            SnippetStatus(
                snippetId = id,
                status = ComplianceStatus.COMPLIANT,
                rulesVersion = 1,
                findingsCount = 0,
            )

        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))
        whenever(statusRepository.findById(id)).thenReturn(Optional.of(status))

        val response = snippetService.getSnippet(id)

        assertEquals(id, response.id)
        assertEquals("Existing Snippet", response.name)
        assertEquals(ComplianceStatus.COMPLIANT, response.status)
    }

    @Test
    fun shouldThrowNotFoundWhenSnippetDoesNotExist() {
        val id = UUID.randomUUID()
        whenever(snippetRepository.findById(id)).thenReturn(Optional.empty())

        assertThrows<ResponseStatusException> {
            snippetService.getSnippet(id)
        }
    }

    @Test
    fun shouldListSnippetsByOwner() {
        val ownerId = "auth0|user123"
        val pageable = PageRequest.of(0, 10)
        val snippet =
            Snippet(
                name = "Snippet 1",
                ownerId = ownerId,
                language = "printscript",
                version = "1.0",
            )
        val page = PageImpl(listOf(snippet))

        whenever(snippetRepository.findAllByOwnerId(ownerId, pageable)).thenReturn(page)
        whenever(statusRepository.findById(snippet.id)).thenReturn(Optional.of(SnippetStatus(snippetId = snippet.id)))

        val result = snippetService.listSnippets(ownerId, pageable)

        assertEquals(1, result.totalElements)
        assertEquals("Snippet 1", result.content[0].name)
    }

    @Test
    fun shouldUpdateSnippetWhenUserIsOwner() {
        val id = UUID.randomUUID()
        val ownerId = "auth0|user123"
        val snippet =
            Snippet(
                id = id,
                name = "Old Name",
                ownerId = ownerId,
                language = "printscript",
                version = "1.0",
            )
        val request = UpdateSnippetRequest(name = "New Name")

        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))
        whenever(snippetRepository.save(any<Snippet>())).thenReturn(snippet)
        whenever(statusRepository.findById(id)).thenReturn(Optional.of(SnippetStatus(snippetId = id)))

        val updated = snippetService.updateSnippet(id, ownerId, request)

        assertEquals("New Name", updated.name)
        verify(snippetRepository).save(snippet)
    }

    @Test
    fun shouldThrowForbiddenWhenUpdatingNonOwnedSnippet() {
        val id = UUID.randomUUID()
        val snippet =
            Snippet(
                id = id,
                name = "Old Name",
                ownerId = "auth0|other",
                language = "printscript",
                version = "1.0",
            )
        val request = UpdateSnippetRequest(name = "New Name")

        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))

        assertThrows<ResponseStatusException> {
            snippetService.updateSnippet(id, "auth0|user123", request)
        }
    }

    @Test
    fun shouldDeleteSnippetWhenUserIsOwner() {
        val id = UUID.randomUUID()
        val ownerId = "auth0|user123"
        val snippet =
            Snippet(
                id = id,
                name = "To Delete",
                ownerId = ownerId,
                language = "printscript",
                version = "1.0",
            )

        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))

        snippetService.deleteSnippet(id, ownerId)

        verify(snippetRepository).delete(snippet)
    }
}
