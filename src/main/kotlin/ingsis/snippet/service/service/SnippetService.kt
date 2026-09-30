package ingsis.snippet.service.service

import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.client.RunnerClient
import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.controller.dto.CreateSnippetRequest
import ingsis.snippet.service.controller.dto.SnippetResponse
import ingsis.snippet.service.controller.dto.UpdateSnippetRequest
import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import ingsis.snippet.service.store.SnippetStore
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class SnippetService(
    private val snippetRepository: SnippetRepository,
    private val statusRepository: SnippetStatusRepository,
    private val runnerClient: RunnerClient,
    private val permissionClient: PermissionClient,
    private val snippetStore: SnippetStore,
) {
    @Transactional
    fun createSnippet(
        ownerId: String,
        request: CreateSnippetRequest,
    ): SnippetResponse {
        validateSnippetSyntax(request.content, request.version)

        val snippet =
            Snippet(
                name = request.name,
                ownerId = ownerId,
                language = request.language,
                version = request.version,
                content = request.content,
            )
        val savedSnippet = snippetRepository.save(snippet)
        snippetStore.put(savedSnippet.id, request.content)

        val status =
            SnippetStatus(
                snippetId = savedSnippet.id,
                status = ComplianceStatus.PENDING,
                rulesVersion = 0,
                findingsCount = 0,
                lastCheckedAt = Instant.now(),
            )
        val savedStatus = statusRepository.save(status)

        permissionClient.assignOwner(savedSnippet.id, ownerId)

        return mapToResponse(savedSnippet, savedStatus, request.content)
    }

    @Transactional(readOnly = true)
    fun getSnippet(
        id: UUID,
        userId: String = "anonymous",
    ): SnippetResponse {
        val snippet =
            snippetRepository.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found")
            }

        val permission = permissionClient.getPermission(id, userId)
        val hasAccess = permission != PermissionLevel.NONE || snippet.ownerId == userId
        if (!hasAccess) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view this snippet")
        }

        val status =
            statusRepository.findById(id).orElse(
                SnippetStatus(snippetId = id),
            )
        val content = snippetStore.get(id) ?: snippet.content
        return mapToResponse(snippet, status, content)
    }

    @Transactional(readOnly = true)
    fun getSnippetContent(
        id: UUID,
        userId: String = "anonymous",
    ): String {
        val snippet =
            snippetRepository.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found")
            }

        val permission = permissionClient.getPermission(id, userId)
        val hasAccess = permission != PermissionLevel.NONE || snippet.ownerId == userId
        if (!hasAccess) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view this snippet")
        }
        return snippetStore.get(id) ?: snippet.content
    }

    @Transactional(readOnly = true)
    fun listSnippets(
        ownerId: String,
        pageable: Pageable,
    ): Page<SnippetResponse> {
        val page = snippetRepository.findAllByOwnerId(ownerId, pageable)
        return page.map { snippet ->
            val status =
                statusRepository.findById(snippet.id).orElse(
                    SnippetStatus(snippetId = snippet.id),
                )
            val content = snippetStore.get(snippet.id) ?: snippet.content
            mapToResponse(snippet, status, content)
        }
    }

    @Transactional
    fun updateSnippet(
        id: UUID,
        ownerId: String,
        request: UpdateSnippetRequest,
    ): SnippetResponse {
        val snippet =
            snippetRepository.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found")
            }
        assertCanEdit(snippet, ownerId)

        request.name?.let { snippet.name = it }
        request.content?.let { newContent ->
            if (newContent != snippet.content) {
                applyContentUpdate(snippet, newContent)
            }
        }
        snippet.updatedAt = Instant.now()
        val updated = snippetRepository.save(snippet)

        val status =
            statusRepository.findById(id).orElse(
                SnippetStatus(snippetId = id),
            )
        val content = snippetStore.get(id) ?: updated.content
        return mapToResponse(updated, status, content)
    }

    @Transactional
    fun deleteSnippet(
        id: UUID,
        ownerId: String,
    ) {
        val snippet =
            snippetRepository.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found")
            }

        val permission = permissionClient.getPermission(id, ownerId)
        val isOwner = permission == PermissionLevel.OWNER || snippet.ownerId == ownerId
        if (!isOwner) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to delete this snippet")
        }
        snippetRepository.delete(snippet)
        snippetStore.delete(id)
    }

    private fun assertCanEdit(
        snippet: Snippet,
        userId: String,
    ) {
        val permission = permissionClient.getPermission(snippet.id, userId)
        val canEdit =
            permission == PermissionLevel.OWNER || permission == PermissionLevel.WRITE || snippet.ownerId == userId
        if (!canEdit) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to edit this snippet")
        }
    }

    private fun applyContentUpdate(
        snippet: Snippet,
        newContent: String,
    ) {
        validateSnippetSyntax(newContent, snippet.version)
        snippet.content = newContent
        snippetStore.put(snippet.id, newContent)
        val status =
            statusRepository.findById(snippet.id).orElse(
                SnippetStatus(snippetId = snippet.id),
            )
        status.status = ComplianceStatus.PENDING
        statusRepository.save(status)
    }

    private fun validateSnippetSyntax(
        content: String,
        version: String,
    ) {
        if (content.isBlank()) return
        val validation = runnerClient.validate(content, version)
        if (!validation.valid) {
            val errorMsg =
                if (validation.errors.isNotEmpty()) {
                    validation.errors.joinToString("; ")
                } else {
                    "Invalid snippet syntax"
                }
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, errorMsg)
        }
    }

    private fun mapToResponse(
        snippet: Snippet,
        status: SnippetStatus,
        content: String = snippet.content,
    ): SnippetResponse =
        SnippetResponse(
            id = snippet.id,
            name = snippet.name,
            ownerId = snippet.ownerId,
            language = snippet.language,
            version = snippet.version,
            content = content,
            status = status.status,
            rulesVersion = status.rulesVersion,
            findingsCount = status.findingsCount,
            createdAt = snippet.createdAt,
            updatedAt = snippet.updatedAt,
        )
}
