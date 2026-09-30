package ingsis.snippet.service.service

import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.client.fake.FakePermissionClient
import ingsis.snippet.service.client.fake.FakeRunnerClient
import ingsis.snippet.service.controller.dto.CreateSnippetRequest
import ingsis.snippet.service.controller.dto.UpdateSnippetRequest
import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import ingsis.snippet.service.store.InMemorySnippetStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class SnippetServiceTest {
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
    fun shouldCreateSnippetSuccessfullyAndAssignOwner() {
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
        assertEquals(PermissionLevel.OWNER, fakePermissionClient.getPermission(response.id, ownerId))
        verify(snippetRepository).save(any<Snippet>())
        verify(statusRepository).save(any<SnippetStatus>())
    }

    @Test
    fun shouldRejectSnippetCreationWhenRunnerDetectsSyntaxError() {
        val ownerId = "auth0|user123"
        val invalidCode = "invalid syntax ;;"
        val request =
            CreateSnippetRequest(
                name = "Broken Snippet",
                language = "printscript",
                version = "1.1",
                content = invalidCode,
            )

        fakeRunnerClient.setValidationResult(
            content = invalidCode,
            valid = false,
            errors = listOf("Unexpected token ';' at line 1, column 16"),
        )

        val exception =
            assertThrows<ResponseStatusException> {
                snippetService.createSnippet(ownerId, request)
            }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        verify(snippetRepository, never()).save(any<Snippet>())
    }

    @Test
    fun shouldGetSnippetByIdWhenUserIsOwner() {
        val id = UUID.randomUUID()
        val ownerId = "auth0|user123"
        val snippet =
            Snippet(
                id = id,
                name = "Existing Snippet",
                ownerId = ownerId,
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

        val response = snippetService.getSnippet(id, ownerId)

        assertEquals(id, response.id)
        assertEquals("Existing Snippet", response.name)
        assertEquals(ComplianceStatus.COMPLIANT, response.status)
    }

    @Test
    fun shouldGetSnippetByIdWhenUserHasReadPermission() {
        val id = UUID.randomUUID()
        val ownerId = "auth0|owner"
        val viewerId = "auth0|viewer"
        val snippet =
            Snippet(
                id = id,
                name = "Shared Snippet",
                ownerId = ownerId,
                language = "printscript",
                version = "1.1",
            )
        val status = SnippetStatus(snippetId = id)

        fakePermissionClient.setPermission(id, viewerId, PermissionLevel.READ)
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))
        whenever(statusRepository.findById(id)).thenReturn(Optional.of(status))

        val response = snippetService.getSnippet(id, viewerId)

        assertEquals(id, response.id)
        assertEquals("Shared Snippet", response.name)
    }

    @Test
    fun shouldThrowForbiddenWhenUserHasNoPermissionToViewSnippet() {
        val id = UUID.randomUUID()
        val snippet =
            Snippet(
                id = id,
                name = "Private Snippet",
                ownerId = "auth0|owner",
                language = "printscript",
                version = "1.1",
            )

        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))

        val exception =
            assertThrows<ResponseStatusException> {
                snippetService.getSnippet(id, "auth0|stranger")
            }

        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
    }

    @Test
    fun shouldThrowNotFoundWhenSnippetDoesNotExist() {
        val id = UUID.randomUUID()
        whenever(snippetRepository.findById(id)).thenReturn(Optional.empty())

        assertThrows<ResponseStatusException> {
            snippetService.getSnippet(id, "auth0|user123")
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

        fakePermissionClient.setPermission(id, ownerId, PermissionLevel.OWNER)
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))
        whenever(snippetRepository.save(any<Snippet>())).thenReturn(snippet)
        whenever(statusRepository.findById(id)).thenReturn(Optional.of(SnippetStatus(snippetId = id)))

        val updated = snippetService.updateSnippet(id, ownerId, request)

        assertEquals("New Name", updated.name)
        verify(snippetRepository).save(snippet)
    }

    @Test
    fun shouldUpdateSnippetWhenUserHasWritePermission() {
        val id = UUID.randomUUID()
        val collaboratorId = "auth0|collaborator"
        val snippet =
            Snippet(
                id = id,
                name = "Shared Code",
                ownerId = "auth0|owner",
                language = "printscript",
                version = "1.0",
            )
        val request = UpdateSnippetRequest(name = "Updated by Collaborator")

        fakePermissionClient.setPermission(id, collaboratorId, PermissionLevel.WRITE)
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))
        whenever(snippetRepository.save(any<Snippet>())).thenReturn(snippet)
        whenever(statusRepository.findById(id)).thenReturn(Optional.of(SnippetStatus(snippetId = id)))

        val updated = snippetService.updateSnippet(id, collaboratorId, request)

        assertEquals("Updated by Collaborator", updated.name)
        verify(snippetRepository).save(snippet)
    }

    @Test
    fun shouldThrowForbiddenWhenUserHasOnlyReadPermissionOnUpdate() {
        val id = UUID.randomUUID()
        val readerId = "auth0|reader"
        val snippet =
            Snippet(
                id = id,
                name = "Read Only Code",
                ownerId = "auth0|owner",
                language = "printscript",
                version = "1.0",
            )
        val request = UpdateSnippetRequest(name = "Attempted Edit")

        fakePermissionClient.setPermission(id, readerId, PermissionLevel.READ)
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))

        val exception =
            assertThrows<ResponseStatusException> {
                snippetService.updateSnippet(id, readerId, request)
            }

        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
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

        fakePermissionClient.setPermission(id, ownerId, PermissionLevel.OWNER)
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))

        snippetService.deleteSnippet(id, ownerId)

        verify(snippetRepository).delete(snippet)
    }

    @Test
    fun shouldThrowForbiddenWhenDeletingSnippetWithOnlyWritePermission() {
        val id = UUID.randomUUID()
        val writerId = "auth0|writer"
        val snippet =
            Snippet(
                id = id,
                name = "Protected Snippet",
                ownerId = "auth0|owner",
                language = "printscript",
                version = "1.0",
            )

        fakePermissionClient.setPermission(id, writerId, PermissionLevel.WRITE)
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))

        val exception =
            assertThrows<ResponseStatusException> {
                snippetService.deleteSnippet(id, writerId)
            }

        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
        verify(snippetRepository, never()).delete(snippet)
    }

    @Test
    fun shouldRevalidateSyntaxAndPersistNewContentOnUpdate() {
        val id = UUID.randomUUID()
        val ownerId = "auth0|owner"
        val snippet =
            Snippet(
                id = id,
                name = "Original",
                ownerId = ownerId,
                language = "printscript",
                version = "1.1",
                content = "let x: number = 1;",
            )
        val request = UpdateSnippetRequest(content = "let x: number = 2;")
        val status = SnippetStatus(snippetId = id, status = ComplianceStatus.COMPLIANT)

        fakePermissionClient.setPermission(id, ownerId, PermissionLevel.OWNER)
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))
        whenever(snippetRepository.save(any<Snippet>())).thenReturn(snippet)
        whenever(statusRepository.findById(id)).thenReturn(Optional.of(status))
        whenever(statusRepository.save(any<SnippetStatus>())).thenReturn(status)

        val updated = snippetService.updateSnippet(id, ownerId, request)

        assertEquals("let x: number = 2;", updated.content)
        assertEquals("let x: number = 2;", snippetStore.get(id))
        assertEquals(ComplianceStatus.PENDING, status.status)
        verify(snippetRepository).save(snippet)
    }

    @Test
    fun shouldThrowBadRequestWhenUpdatingWithInvalidContentSyntax() {
        val id = UUID.randomUUID()
        val ownerId = "auth0|owner"
        val snippet =
            Snippet(
                id = id,
                name = "Original",
                ownerId = ownerId,
                language = "printscript",
                version = "1.1",
                content = "let x: number = 1;",
            )
        val request = UpdateSnippetRequest(content = "invalid syntax code")

        fakePermissionClient.setPermission(id, ownerId, PermissionLevel.OWNER)
        fakeRunnerClient.setValidationResult("invalid syntax code", false, listOf("Syntax error at line 1"))
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))

        val exception =
            assertThrows<ResponseStatusException> {
                snippetService.updateSnippet(id, ownerId, request)
            }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun shouldGetSnippetContentFromStore() {
        val id = UUID.randomUUID()
        val ownerId = "auth0|owner"
        val snippet =
            Snippet(
                id = id,
                name = "Original",
                ownerId = ownerId,
                language = "printscript",
                version = "1.1",
                content = "initial content",
            )
        snippetStore.put(id, "stored blob content")

        fakePermissionClient.setPermission(id, ownerId, PermissionLevel.OWNER)
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))

        val content = snippetService.getSnippetContent(id, ownerId)

        assertEquals("stored blob content", content)
    }

    @Test
    fun shouldDeleteSnippetFromStoreWhenDeleted() {
        val id = UUID.randomUUID()
        val ownerId = "auth0|owner"
        val snippet =
            Snippet(
                id = id,
                name = "To Delete",
                ownerId = ownerId,
                language = "printscript",
                version = "1.1",
            )
        snippetStore.put(id, "content to delete")

        fakePermissionClient.setPermission(id, ownerId, PermissionLevel.OWNER)
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))

        snippetService.deleteSnippet(id, ownerId)

        assertNull(snippetStore.get(id))
        verify(snippetRepository).delete(snippet)
    }

    @Test
    fun shouldFormatSnippetUsingUserRules() {
        val id = UUID.randomUUID()
        val ownerId = "auth0|owner"
        val snippet =
            Snippet(
                id = id,
                name = "To Format",
                ownerId = ownerId,
                language = "printscript",
                version = "1.1",
                content = "let a=1;",
            )
        snippetStore.put(id, "let a=1;")
        fakePermissionClient.setPermission(id, ownerId, PermissionLevel.OWNER)
        fakePermissionClient.setFormatRules(ownerId, mapOf("rules" to true))
        fakeRunnerClient.defaultFormattedContent = "let a = 1;"
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))

        val formatted = snippetService.formatSnippet(id, ownerId)

        assertEquals("let a = 1;", formatted)
    }

    @Test
    fun shouldLintSnippetAndMarkCompliantWhenNoFindings() {
        val id = UUID.randomUUID()
        val ownerId = "auth0|owner"
        val snippet =
            Snippet(
                id = id,
                name = "Clean Snippet",
                ownerId = ownerId,
                language = "printscript",
                version = "1.1",
                content = "let a: number = 1;",
            )
        val status = SnippetStatus(snippetId = id, status = ComplianceStatus.PENDING)
        snippetStore.put(id, "let a: number = 1;")
        fakePermissionClient.setPermission(id, ownerId, PermissionLevel.OWNER)
        fakeRunnerClient.defaultLintFindings = emptyList()
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))
        whenever(statusRepository.findById(id)).thenReturn(Optional.of(status))
        whenever(statusRepository.save(any<SnippetStatus>())).thenReturn(status)

        val report = snippetService.lintSnippet(id, ownerId)

        assertEquals(ComplianceStatus.COMPLIANT, report.status)
        assertEquals(0, report.findingsCount)
    }

    @Test
    fun shouldLintSnippetAndMarkNonCompliantWhenFindingsExist() {
        val id = UUID.randomUUID()
        val ownerId = "auth0|owner"
        val snippet =
            Snippet(
                id = id,
                name = "Dirty Snippet",
                ownerId = ownerId,
                language = "printscript",
                version = "1.1",
                content = "let a: number = 1;",
            )
        val status = SnippetStatus(snippetId = id, status = ComplianceStatus.PENDING)
        snippetStore.put(id, "let a: number = 1;")
        fakePermissionClient.setPermission(id, ownerId, PermissionLevel.OWNER)
        fakeRunnerClient.defaultLintFindings =
            listOf(
                ingsis.snippet.service.client.dto
                    .LintFindingClientDto("Style violation", 1, 1)
            )
        whenever(snippetRepository.findById(id)).thenReturn(Optional.of(snippet))
        whenever(statusRepository.findById(id)).thenReturn(Optional.of(status))
        whenever(statusRepository.save(any<SnippetStatus>())).thenReturn(status)

        val report = snippetService.lintSnippet(id, ownerId)

        assertEquals(ComplianceStatus.NOT_COMPLIANT, report.status)
        assertEquals(1, report.findingsCount)
        assertEquals("Style violation", report.findings[0].message)
    }
}
