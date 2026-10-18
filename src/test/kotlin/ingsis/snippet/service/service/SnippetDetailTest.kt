package ingsis.snippet.service.service

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.client.dto.LintFindingClientDto
import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.client.fake.FakePermissionClient
import ingsis.snippet.service.client.fake.FakeRunnerClient
import ingsis.snippet.service.controller.dto.CreateSnippetRequest
import ingsis.snippet.service.controller.dto.UpdateSnippetRequest
import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.domain.model.LintFindingsCodec
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import ingsis.snippet.service.store.InMemorySnippetStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

/**
 * Detalle de un snippet (US #6): descripción propia del snippet y las reglas de linteo que
 * infringió, guardadas junto al estado para no tener que volver a lintear en cada lectura.
 */
class SnippetDetailTest {
    private lateinit var snippetRepository: SnippetRepository
    private lateinit var statusRepository: SnippetStatusRepository
    private lateinit var fakeRunnerClient: FakeRunnerClient
    private lateinit var fakePermissionClient: FakePermissionClient
    private lateinit var snippetStore: InMemorySnippetStore
    private lateinit var snippetService: SnippetService

    private val findingsCodec = LintFindingsCodec(ObjectMapper())
    private val ownerId = "auth0|owner"
    private val snippetId = UUID.randomUUID()

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
                findingsCodec = findingsCodec,
            )
        fakePermissionClient.setPermission(snippetId, ownerId, PermissionLevel.OWNER)
        snippetStore.put(snippetId, "let a: number = 1;")
    }

    @Test
    fun shouldPersistTheDescriptionGivenWhenTheSnippetIsCreated() {
        val request =
            CreateSnippetRequest(
                name = "Documented",
                language = "printscript",
                version = "1.1",
                content = "let a: number = 1;",
                description = "Calcula el promedio de las notas",
            )
        whenever(snippetRepository.save(any<Snippet>())).thenAnswer { it.arguments[0] }
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        val response = snippetService.createSnippet(ownerId, request)

        assertEquals("Calcula el promedio de las notas", response.description)
    }

    @Test
    fun shouldReplaceTheDescriptionOnUpdateAndLeaveItUntouchedWhenItIsNotSent() {
        val snippet = storedSnippet(description = "Versión vieja")
        whenever(snippetRepository.findById(snippetId)).thenReturn(Optional.of(snippet))
        whenever(snippetRepository.save(any<Snippet>())).thenAnswer { it.arguments[0] }
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(SnippetStatus(snippetId = snippetId)))

        val described = snippetService.updateSnippet(snippetId, ownerId, UpdateSnippetRequest(description = "Nueva"))
        assertEquals("Nueva", described.description)

        val renamed = snippetService.updateSnippet(snippetId, ownerId, UpdateSnippetRequest(name = "Otro nombre"))
        assertEquals("Nueva", renamed.description)
    }

    @Test
    fun shouldStoreTheLintFindingsSoTheDetailCanShowThemWithoutLintingAgain() {
        val snippet = storedSnippet()
        val status = SnippetStatus(snippetId = snippetId, status = ComplianceStatus.PENDING)
        fakeRunnerClient.defaultLintFindings =
            listOf(
                LintFindingClientDto("Identifier 'A' is not in camel case", 1, 5),
                LintFindingClientDto("println only accepts a variable or a literal", 3, 1),
            )
        whenever(snippetRepository.findById(snippetId)).thenReturn(Optional.of(snippet))
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(status))
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        snippetService.lintSnippet(snippetId, ownerId)
        val detail = snippetService.getSnippet(snippetId, ownerId)

        assertEquals(ComplianceStatus.NOT_COMPLIANT, detail.status)
        assertEquals(2, detail.findingsCount)
        assertEquals("Identifier 'A' is not in camel case", detail.findings?.first()?.message)
        assertEquals(5, detail.findings?.first()?.column)
    }

    @Test
    fun shouldClearTheStoredFindingsWhenTheContentChangesAndTheStatusGoesBackToPending() {
        val snippet = storedSnippet()
        val status =
            SnippetStatus(
                snippetId = snippetId,
                status = ComplianceStatus.NOT_COMPLIANT,
                findingsCount = 1,
                findings = """[{"message":"Style violation","line":1,"column":1}]""",
            )
        whenever(snippetRepository.findById(snippetId)).thenReturn(Optional.of(snippet))
        whenever(snippetRepository.save(any<Snippet>())).thenAnswer { it.arguments[0] }
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(status))
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        val updated =
            snippetService.updateSnippet(
                snippetId,
                ownerId,
                UpdateSnippetRequest(content = "let b: number = 2;")
            )

        assertEquals(ComplianceStatus.PENDING, updated.status)
        assertEquals(0, updated.findingsCount)
        assertTrue(updated.findings.isNullOrEmpty())
    }

    @Test
    fun shouldNotIncludeFindingsWhenNoLintHasEverRun() {
        val snippet = storedSnippet()
        whenever(snippetRepository.findById(snippetId)).thenReturn(Optional.of(snippet))
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(SnippetStatus(snippetId = snippetId)))

        val detail = snippetService.getSnippet(snippetId, ownerId)

        assertEquals(emptyList<LintFindingClientDto>(), detail.findings)
        assertNull(detail.description)
    }

    private fun storedSnippet(description: String? = null): Snippet =
        Snippet(
            id = snippetId,
            name = "Snippet",
            ownerId = ownerId,
            language = "printscript",
            version = "1.1",
            description = description,
        )
}
