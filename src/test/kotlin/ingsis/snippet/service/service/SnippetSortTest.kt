package ingsis.snippet.service.service

import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.client.fake.FakePermissionClient
import ingsis.snippet.service.client.fake.FakeRunnerClient
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.model.SnippetScope
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import ingsis.snippet.service.store.InMemorySnippetStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.JpaSort
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * El estado de cumplimiento no es una columna de `snippets`, así que ordenar por él tiene que
 * traducirse al alias del `LEFT JOIN` antes de llegar a la JPQL.
 */
class SnippetSortTest {
    private lateinit var snippetRepository: SnippetRepository
    private lateinit var statusRepository: SnippetStatusRepository
    private lateinit var snippetService: SnippetService
    private lateinit var fakePermissionClient: FakePermissionClient
    private lateinit var snippet: Snippet

    private val userId = "auth0|user123"

    @BeforeEach
    fun setUp() {
        snippetRepository = mock()
        statusRepository = mock()
        fakePermissionClient = FakePermissionClient()
        snippetService =
            SnippetService(
                snippetRepository = snippetRepository,
                statusRepository = statusRepository,
                runnerClient = FakeRunnerClient(),
                permissionClient = fakePermissionClient,
                snippetStore = InMemorySnippetStore(),
            )
        snippet = Snippet(name = "Mine", ownerId = userId, language = "printscript", version = "1.1")
        fakePermissionClient.setPermission(snippet.id, userId, PermissionLevel.OWNER)
        whenever(
            snippetRepository.findAllByIdsAndFilters(any(), anyOrNull(), anyOrNull(), anyOrNull(), any()),
        ).thenReturn(PageImpl(listOf(snippet)))
        whenever(statusRepository.findAllById(any<List<UUID>>())).thenReturn(emptyList())
    }

    @Test
    fun shouldTranslateStatusOrderingToTheJoinedStatusEntity() {
        listSortedBy(Sort.by(Sort.Direction.DESC, "status"))

        val order = capturedSort().getOrderFor("st.status")
        assertEquals(Sort.Direction.DESC, order?.direction)
        assertTrue(order is JpaSort.JpaOrder && order.isUnsafe)
    }

    @Test
    fun shouldAcceptTheNameTheInterfaceUsesForComplianceOrdering() {
        listSortedBy(Sort.by("compliance"))

        assertEquals(Sort.Direction.ASC, capturedSort().getOrderFor("st.status")?.direction)
    }

    @Test
    fun shouldKeepOrderingByColumnsThatBelongToTheSnippetItself() {
        listSortedBy(Sort.by(Sort.Direction.DESC, "createdAt"))

        assertEquals(Sort.Direction.DESC, capturedSort().getOrderFor("createdAt")?.direction)
    }

    @Test
    fun shouldPreserveTheOrderOfEveryRequestedSortCriterion() {
        listSortedBy(Sort.by("status").and(Sort.by(Sort.Direction.DESC, "name")))

        val orders = capturedSort().toList()
        assertEquals(listOf("st.status", "name"), orders.map { it.property })
        assertEquals(Sort.Direction.DESC, orders[1].direction)
    }

    @Test
    fun shouldRejectSortingByAPropertyThatIsNotPartOfTheListing() {
        val exception =
            assertThrows<ResponseStatusException> {
                listSortedBy(Sort.by("ownerId"))
            }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        verify(snippetRepository, never()).findAllByIdsAndFilters(any(), anyOrNull(), anyOrNull(), anyOrNull(), any())
    }

    private fun listSortedBy(sort: Sort) {
        snippetService.listSnippets(
            userId = userId,
            scope = SnippetScope.OWNED,
            pageable = PageRequest.of(0, 10, sort),
        )
    }

    private fun capturedSort(): Sort {
        val captor = argumentCaptor<Pageable>()
        verify(snippetRepository).findAllByIdsAndFilters(
            any(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            captor.capture(),
        )
        return captor.firstValue.sort
    }
}
