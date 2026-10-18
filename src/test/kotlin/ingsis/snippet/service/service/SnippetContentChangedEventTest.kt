package ingsis.snippet.service.service

import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.client.fake.FakePermissionClient
import ingsis.snippet.service.client.fake.FakeRunnerClient
import ingsis.snippet.service.controller.dto.CreateSnippetRequest
import ingsis.snippet.service.controller.dto.UpdateSnippetRequest
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import ingsis.snippet.service.store.InMemorySnippetStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID

/**
 * El alta y el update de contenido anuncian el cambio con [SnippetContentChangedEvent], que es
 * lo que dispara la corrida de tests (US #16) y el pedido de linteo (US #15) después del commit.
 */
class SnippetContentChangedEventTest {
    private lateinit var snippetRepository: SnippetRepository
    private lateinit var statusRepository: SnippetStatusRepository
    private lateinit var fakeRunnerClient: FakeRunnerClient
    private lateinit var fakePermissionClient: FakePermissionClient
    private lateinit var snippetStore: InMemorySnippetStore
    private lateinit var snippetTestRunner: SnippetTestRunner

    @BeforeEach
    fun setUp() {
        snippetRepository = mock()
        statusRepository = mock()
        fakeRunnerClient = FakeRunnerClient()
        fakePermissionClient = FakePermissionClient()
        snippetStore = InMemorySnippetStore()
        snippetTestRunner = mock()
    }

    private fun serviceWithPublisher(eventPublisher: ApplicationEventPublisher): SnippetService =
        SnippetService(
            snippetRepository = snippetRepository,
            statusRepository = statusRepository,
            runnerClient = fakeRunnerClient,
            permissionClient = fakePermissionClient,
            snippetStore = snippetStore,
            snippetTestRunner = snippetTestRunner,
            eventPublisher = eventPublisher,
        )

    @Test
    fun shouldAnnounceTheNewContentWithItsOwnerWhenASnippetIsCreated() {
        val eventPublisher: ApplicationEventPublisher = mock()
        val ownerId = "auth0|creator"
        val request =
            CreateSnippetRequest(name = "New", language = "printscript", version = "1.1", content = "println(1);")
        val snippet =
            Snippet(name = request.name, ownerId = ownerId, language = request.language, version = request.version)
        whenever(snippetRepository.save(any<Snippet>())).thenReturn(snippet)
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        serviceWithPublisher(eventPublisher).createSnippet(ownerId, request)

        verify(eventPublisher).publishEvent(SnippetContentChangedEvent(snippet.id, "println(1);", "1.1", ownerId))
    }

    @Test
    fun shouldAnnounceTheNewContentWithTheRegisteredOwnerWhenACollaboratorEdits() {
        val eventPublisher: ApplicationEventPublisher = mock()
        val id = UUID.randomUUID()
        val snippet =
            Snippet(id = id, name = "Shared", ownerId = "auth0|owner", language = "printscript", version = "1.1")
        snippetStore.put(id, "println(1);")
        fakePermissionClient.setPermission(id, "auth0|collaborator", PermissionLevel.WRITE)
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))
        whenever(snippetRepository.save(any<Snippet>())).thenReturn(snippet)
        whenever(statusRepository.findById(id)).thenReturn(Optional.of(SnippetStatus(snippetId = id)))
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        serviceWithPublisher(
            eventPublisher
        ).updateSnippet(id, "auth0|collaborator", UpdateSnippetRequest(content = "println(2);"))

        verify(eventPublisher).publishEvent(SnippetContentChangedEvent(id, "println(2);", "1.1", "auth0|owner"))
    }

    @Test
    fun shouldNotAnnounceAnythingWhenOnlyTheDescriptionChanges() {
        val eventPublisher: ApplicationEventPublisher = mock()
        val id = UUID.randomUUID()
        val snippet =
            Snippet(id = id, name = "Same", ownerId = "auth0|owner", language = "printscript", version = "1.1")
        snippetStore.put(id, "println(1);")
        fakePermissionClient.setPermission(id, "auth0|owner", PermissionLevel.OWNER)
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))
        whenever(snippetRepository.save(any<Snippet>())).thenReturn(snippet)
        whenever(statusRepository.findById(id)).thenReturn(Optional.of(SnippetStatus(snippetId = id)))

        serviceWithPublisher(
            eventPublisher
        ).updateSnippet(id, "auth0|owner", UpdateSnippetRequest(description = "Nueva"))

        verify(eventPublisher, never()).publishEvent(any<SnippetContentChangedEvent>())
    }
}
