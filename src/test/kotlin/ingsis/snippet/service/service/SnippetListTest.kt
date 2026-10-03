package ingsis.snippet.service.service

import ingsis.snippet.service.client.PermissionClient
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class SnippetListTest {
    private lateinit var snippetRepository: SnippetRepository
    private lateinit var statusRepository: SnippetStatusRepository
    private lateinit var fakeRunnerClient: FakeRunnerClient
    private lateinit var fakePermissionClient: FakePermissionClient
    private lateinit var snippetStore: InMemorySnippetStore
    private lateinit var snippetService: SnippetService

    private val userId = "auth0|user123"
    private val pageable = PageRequest.of(0, 10)

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

    private fun snippetNamed(
        name: String,
        creator: String = userId,
    ): Snippet = Snippet(name = name, ownerId = creator, language = "printscript", version = "1.1")

    @Test
    fun shouldListOnlyOwnedSnippetsAccordingToPermissionService() {
        val owned = snippetNamed("Mine")
        val shared = snippetNamed("Shared with me", creator = "auth0|other")
        fakePermissionClient.setPermission(owned.id, userId, PermissionLevel.OWNER)
        fakePermissionClient.setPermission(shared.id, userId, PermissionLevel.READ)
        whenever(
            snippetRepository.findAllByIdsAndFilters(
                eq(setOf(owned.id)),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                eq(pageable)
            )
        ).thenReturn(PageImpl(listOf(owned)))
        whenever(statusRepository.findAllById(listOf(owned.id))).thenReturn(listOf(SnippetStatus(snippetId = owned.id)))

        val result = snippetService.listSnippets(userId = userId, scope = SnippetScope.OWNED, pageable = pageable)

        assertEquals(1, result.totalElements)
        assertEquals("Mine", result.content[0].name)
    }

    @Test
    fun shouldTreatTransferredSnippetAsOwnedByTheNewOwnerEvenIfCreatedByAnotherUser() {
        val transferred = snippetNamed("Inherited", creator = "auth0|previous-owner")
        fakePermissionClient.setPermission(transferred.id, userId, PermissionLevel.OWNER)
        whenever(
            snippetRepository.findAllByIdsAndFilters(
                eq(setOf(transferred.id)),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                eq(pageable)
            )
        ).thenReturn(PageImpl(listOf(transferred)))
        whenever(statusRepository.findAllById(listOf(transferred.id))).thenReturn(emptyList())

        val result = snippetService.listSnippets(userId = userId, scope = SnippetScope.OWNED, pageable = pageable)

        assertEquals(1, result.totalElements)
        assertEquals("Inherited", result.content[0].name)
    }

    @Test
    fun shouldListSharedSnippetsWithReadOrWriteLevel() {
        val readable = snippetNamed("Readable", creator = "auth0|other")
        val writable = snippetNamed("Writable", creator = "auth0|other")
        val owned = snippetNamed("Mine")
        fakePermissionClient.setPermission(readable.id, userId, PermissionLevel.READ)
        fakePermissionClient.setPermission(writable.id, userId, PermissionLevel.WRITE)
        fakePermissionClient.setPermission(owned.id, userId, PermissionLevel.OWNER)
        whenever(
            snippetRepository.findAllByIdsAndFilters(
                eq(setOf(readable.id, writable.id)),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                eq(pageable),
            ),
        ).thenReturn(PageImpl(listOf(readable, writable)))
        whenever(statusRepository.findAllById(any<List<UUID>>())).thenReturn(emptyList())

        val result = snippetService.listSnippets(userId = userId, scope = SnippetScope.SHARED, pageable = pageable)

        assertEquals(2, result.totalElements)
    }

    @Test
    fun shouldReturnEmptyPageWithoutQueryingDatabaseWhenUserHasNoAccessibleSnippets() {
        val result = snippetService.listSnippets(userId = "auth0|lonely", scope = SnippetScope.ALL, pageable = pageable)

        assertEquals(0, result.totalElements)
        verify(snippetRepository, never()).findAllByIdsAndFilters(any(), anyOrNull(), anyOrNull(), anyOrNull(), any())
    }

    @Test
    fun shouldListOwnedAndSharedTogetherInAllScope() {
        val owned = snippetNamed("Mine")
        val shared = snippetNamed("Shared", creator = "auth0|other")
        fakePermissionClient.setPermission(owned.id, userId, PermissionLevel.OWNER)
        fakePermissionClient.setPermission(shared.id, userId, PermissionLevel.WRITE)
        whenever(
            snippetRepository.findAllByIdsAndFilters(
                eq(setOf(owned.id, shared.id)),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                eq(pageable),
            ),
        ).thenReturn(PageImpl(listOf(owned, shared)))
        whenever(statusRepository.findAllById(any<List<UUID>>())).thenReturn(emptyList())

        val result = snippetService.listSnippets(userId = userId, scope = SnippetScope.ALL, pageable = pageable)

        assertEquals(2, result.totalElements)
    }

    @Test
    fun shouldPassFiltersThroughToRepository() {
        val owned = snippetNamed("Calculadora")
        fakePermissionClient.setPermission(owned.id, userId, PermissionLevel.OWNER)
        whenever(
            snippetRepository.findAllByIdsAndFilters(
                setOf(owned.id),
                "Calculadora",
                "printscript",
                ComplianceStatus.COMPLIANT,
                pageable,
            ),
        ).thenReturn(PageImpl(listOf(owned)))
        whenever(statusRepository.findAllById(listOf(owned.id)))
            .thenReturn(listOf(SnippetStatus(snippetId = owned.id, status = ComplianceStatus.COMPLIANT)))

        val result =
            snippetService.listSnippets(
                userId = userId,
                scope = SnippetScope.OWNED,
                name = "Calculadora",
                language = "printscript",
                status = ComplianceStatus.COMPLIANT,
                pageable = pageable,
            )

        assertEquals(1, result.totalElements)
        assertEquals(ComplianceStatus.COMPLIANT, result.content[0].status)
    }

    @Test
    fun shouldOmitContentInListings() {
        val owned = snippetNamed("Mine")
        snippetStore.put(owned.id, "println(1);")
        fakePermissionClient.setPermission(owned.id, userId, PermissionLevel.OWNER)
        whenever(
            snippetRepository.findAllByIdsAndFilters(
                eq(setOf(owned.id)),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                eq(pageable)
            )
        ).thenReturn(PageImpl(listOf(owned)))
        whenever(statusRepository.findAllById(listOf(owned.id))).thenReturn(emptyList())

        val result = snippetService.listSnippets(userId = userId, scope = SnippetScope.OWNED, pageable = pageable)

        assertNull(result.content[0].content)
    }

    @Test
    fun shouldAnswerServiceUnavailableWhenPermissionServiceCannotBeReached() {
        val failingPermissionClient: PermissionClient = mock()
        whenever(failingPermissionClient.getUserPermissions(eq(userId), anyOrNull()))
            .thenThrow(ResourceAccessException("connection refused"))
        val service =
            SnippetService(
                snippetRepository = snippetRepository,
                statusRepository = statusRepository,
                runnerClient = fakeRunnerClient,
                permissionClient = failingPermissionClient,
                snippetStore = snippetStore,
            )

        val exception =
            assertThrows<ResponseStatusException> {
                service.listSnippets(userId = userId, scope = SnippetScope.ALL, pageable = pageable)
            }

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.statusCode)
    }
}
