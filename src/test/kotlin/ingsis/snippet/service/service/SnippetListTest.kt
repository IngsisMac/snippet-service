package ingsis.snippet.service.service

import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.client.fake.FakePermissionClient
import ingsis.snippet.service.client.fake.FakeRunnerClient
import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.model.SnippetScope
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import ingsis.snippet.service.store.InMemorySnippetStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.UUID

class SnippetListTest {
    private lateinit var snippetRepository: SnippetRepository
    private lateinit var statusRepository: SnippetStatusRepository
    private lateinit var fakeRunnerClient: FakeRunnerClient
    private lateinit var fakePermissionClient: FakePermissionClient
    private lateinit var snippetStore: InMemorySnippetStore
    private lateinit var snippetService: SnippetService

    @BeforeEach
    fun setUp() {
        snippetRepository = mock()
        statusRepository = mock()
        fakeRunnerClient = FakeRunnerClient()
        fakePermissionClient = FakePermissionClient()
        snippetStore = InMemorySnippetStore()
        snippetService =
            SnippetService(
                snippetRepository = snippetRepository,
                statusRepository = statusRepository,
                runnerClient = fakeRunnerClient,
                permissionClient = fakePermissionClient,
                snippetStore = snippetStore,
            )
    }

    @Test
    fun shouldListSnippetsWithDefaultAllScope() {
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
        val status = SnippetStatus(snippetId = snippet.id)

        whenever(
            snippetRepository.findAllByOwnerAndFilters(
                ownerId = ownerId,
                name = null,
                language = null,
                status = null,
                pageable = pageable,
            ),
        ).thenReturn(page)
        whenever(statusRepository.findAllById(listOf(snippet.id))).thenReturn(listOf(status))

        val result = snippetService.listSnippets(ownerId, pageable)

        assertEquals(1, result.totalElements)
        assertEquals("Snippet 1", result.content[0].name)
    }

    @Test
    fun shouldListSnippetsWithOwnedScope() {
        val ownerId = "auth0|user123"
        val pageable = PageRequest.of(0, 10)
        val snippet =
            Snippet(
                name = "My Snippet",
                ownerId = ownerId,
                language = "printscript",
                version = "1.1",
            )
        val page = PageImpl(listOf(snippet))
        val status = SnippetStatus(snippetId = snippet.id)

        whenever(
            snippetRepository.findAllByOwnerAndFilters(
                ownerId = ownerId,
                name = null,
                language = null,
                status = null,
                pageable = pageable,
            ),
        ).thenReturn(page)
        whenever(statusRepository.findAllById(listOf(snippet.id))).thenReturn(listOf(status))

        val result =
            snippetService.listSnippets(
                userId = ownerId,
                scope = SnippetScope.OWNED,
                pageable = pageable,
            )

        assertEquals(1, result.totalElements)
        assertEquals("My Snippet", result.content[0].name)
    }

    @Test
    fun shouldListSharedSnippetsWhenUserHasPermissions() {
        val userId = "auth0|collaborator"
        val sharedSnippetId = UUID.randomUUID()
        val pageable = PageRequest.of(0, 10)
        val sharedSnippet =
            Snippet(
                id = sharedSnippetId,
                name = "Shared Code",
                ownerId = "auth0|other",
                language = "printscript",
                version = "1.1",
            )
        val page = PageImpl(listOf(sharedSnippet))
        val status = SnippetStatus(snippetId = sharedSnippetId)

        fakePermissionClient.setPermission(sharedSnippetId, userId, PermissionLevel.READ)
        whenever(
            snippetRepository.findAllByIdsAndFilters(
                ids = setOf(sharedSnippetId),
                name = null,
                language = null,
                status = null,
                pageable = pageable,
            ),
        ).thenReturn(page)
        whenever(statusRepository.findAllById(listOf(sharedSnippetId))).thenReturn(listOf(status))

        val result =
            snippetService.listSnippets(
                userId = userId,
                scope = SnippetScope.SHARED,
                pageable = pageable,
            )

        assertEquals(1, result.totalElements)
        assertEquals("Shared Code", result.content[0].name)
    }

    @Test
    fun shouldReturnEmptyPageWhenUserHasNoSharedSnippetsInSharedScope() {
        val userId = "auth0|lonely"
        val pageable = PageRequest.of(0, 10)

        val result =
            snippetService.listSnippets(
                userId = userId,
                scope = SnippetScope.SHARED,
                pageable = pageable,
            )

        assertEquals(0, result.totalElements)
        verify(snippetRepository, never()).findAllByIdsAndFilters(any(), any(), any(), any(), any())
    }

    @Test
    fun shouldListAllSnippetsWhenUserHasBothOwnedAndSharedSnippets() {
        val userId = "auth0|user123"
        val sharedSnippetId = UUID.randomUUID()
        val pageable = PageRequest.of(0, 10)
        val snippet =
            Snippet(
                name = "Snippet 1",
                ownerId = userId,
                language = "printscript",
                version = "1.0",
            )
        val page = PageImpl(listOf(snippet))
        val status = SnippetStatus(snippetId = snippet.id)

        fakePermissionClient.setPermission(sharedSnippetId, userId, PermissionLevel.WRITE)
        whenever(
            snippetRepository.findAllByOwnerOrIdsAndFilters(
                ownerId = userId,
                ids = setOf(sharedSnippetId),
                name = null,
                language = null,
                status = null,
                pageable = pageable,
            ),
        ).thenReturn(page)
        whenever(statusRepository.findAllById(listOf(snippet.id))).thenReturn(listOf(status))

        val result =
            snippetService.listSnippets(
                userId = userId,
                scope = SnippetScope.ALL,
                pageable = pageable,
            )

        assertEquals(1, result.totalElements)
    }

    @Test
    fun shouldFilterSnippetsByNameLanguageAndStatus() {
        val ownerId = "auth0|user123"
        val pageable = PageRequest.of(0, 10)
        val snippet =
            Snippet(
                name = "Calculadora",
                ownerId = ownerId,
                language = "printscript",
                version = "1.1",
            )
        val page = PageImpl(listOf(snippet))
        val status = SnippetStatus(snippetId = snippet.id, status = ComplianceStatus.COMPLIANT)

        whenever(
            snippetRepository.findAllByOwnerAndFilters(
                ownerId = ownerId,
                name = "Calculadora",
                language = "printscript",
                status = ComplianceStatus.COMPLIANT,
                pageable = pageable,
            ),
        ).thenReturn(page)
        whenever(statusRepository.findAllById(listOf(snippet.id))).thenReturn(listOf(status))

        val result =
            snippetService.listSnippets(
                userId = ownerId,
                scope = SnippetScope.OWNED,
                name = "Calculadora",
                language = "printscript",
                status = ComplianceStatus.COMPLIANT,
                pageable = pageable,
            )

        assertEquals(1, result.totalElements)
        assertEquals("Calculadora", result.content[0].name)
    }
}
