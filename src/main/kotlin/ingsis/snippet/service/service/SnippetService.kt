package ingsis.snippet.service.service

import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.client.RunnerClient
import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.controller.dto.CreateSnippetRequest
import ingsis.snippet.service.controller.dto.LintReportResponse
import ingsis.snippet.service.controller.dto.SnippetResponse
import ingsis.snippet.service.controller.dto.UpdateSnippetRequest
import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.model.SnippetScope
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import ingsis.snippet.service.store.SnippetStore
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Toda decisión de acceso (ver, editar, borrar) se resuelve contra `permission-service` vía
 * [PermissionClient]. La columna `owner_id` de `snippets` registra quién lo creó y sirve
 * para el listado por autor, pero nunca otorga permisos por sí misma: así una
 * transferencia de ownership hecha en permisos tiene efecto inmediato acá.
 */
@Service
class SnippetService(
    private val snippetRepository: SnippetRepository,
    private val statusRepository: SnippetStatusRepository,
    private val runnerClient: RunnerClient,
    private val permissionClient: PermissionClient,
    private val snippetStore: SnippetStore,
    private val snippetTestRunner: SnippetTestRunner? = null,
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

        assertCanView(snippet, userId)

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

        assertCanView(snippet, userId)
        return snippetStore.get(id) ?: snippet.content
    }

    @Transactional(readOnly = true)
    fun formatSnippet(
        id: UUID,
        userId: String = "anonymous",
    ): String {
        val snippet =
            snippetRepository.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found")
            }
        assertCanView(snippet, userId)

        val rules = permissionClient.getFormatRules(userId)
        val content = snippetStore.get(id) ?: snippet.content
        return runnerClient.format(content, snippet.version, rules)
    }

    @Transactional
    fun lintSnippet(
        id: UUID,
        userId: String = "anonymous",
    ): LintReportResponse {
        val snippet =
            snippetRepository.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found")
            }
        assertCanView(snippet, userId)

        val rules = permissionClient.getLintRules(userId)
        val content = snippetStore.get(id) ?: snippet.content
        val result = runnerClient.lint(content, snippet.version, rules)
        val complianceStatus = updateLintComplianceStatus(id, result.findingsCount)

        return LintReportResponse(
            snippetId = id,
            status = complianceStatus,
            findings = result.findings,
            findingsCount = result.findingsCount,
        )
    }

    private fun updateLintComplianceStatus(
        snippetId: UUID,
        findingsCount: Int,
    ): ComplianceStatus {
        val status = statusRepository.findById(snippetId).orElse(SnippetStatus(snippetId = snippetId))
        status.status = if (findingsCount == 0) ComplianceStatus.COMPLIANT else ComplianceStatus.NOT_COMPLIANT
        status.findingsCount = findingsCount
        status.lastCheckedAt = Instant.now()
        return statusRepository.save(status).status
    }

    @Transactional(readOnly = true)
    fun listSnippets(
        ownerId: String,
        pageable: Pageable,
    ): Page<SnippetResponse> = listSnippets(userId = ownerId, scope = SnippetScope.ALL, pageable = pageable)

    @Transactional(readOnly = true)
    @Suppress("LongParameterList")
    fun listSnippets(
        userId: String,
        scope: SnippetScope = SnippetScope.ALL,
        name: String? = null,
        language: String? = null,
        status: ComplianceStatus? = null,
        pageable: Pageable,
    ): Page<SnippetResponse> {
        val page = fetchSnippetsPage(userId, scope, name, language, status, pageable)
        return enrichSnippetResponses(page)
    }

    @Suppress("LongParameterList")
    private fun fetchSnippetsPage(
        userId: String,
        scope: SnippetScope,
        name: String?,
        language: String?,
        status: ComplianceStatus?,
        pageable: Pageable,
    ): Page<Snippet> =
        when (scope) {
            SnippetScope.OWNED -> {
                snippetRepository.findAllByOwnerAndFilters(userId, name, language, status, pageable)
            }

            SnippetScope.SHARED -> {
                val sharedIds = getSharedSnippetIds(userId)
                if (sharedIds.isEmpty()) {
                    PageImpl(emptyList(), pageable, 0)
                } else {
                    snippetRepository.findAllByIdsAndFilters(sharedIds, name, language, status, pageable)
                }
            }

            SnippetScope.ALL -> {
                val sharedIds = getSharedSnippetIds(userId)
                if (sharedIds.isEmpty()) {
                    snippetRepository.findAllByOwnerAndFilters(userId, name, language, status, pageable)
                } else {
                    snippetRepository.findAllByOwnerOrIdsAndFilters(userId, sharedIds, name, language, status, pageable)
                }
            }
        }

    private fun enrichSnippetResponses(page: Page<Snippet>): Page<SnippetResponse> {
        val snippetIds = page.content.map { it.id }
        val statusMap =
            if (snippetIds.isEmpty()) {
                emptyMap()
            } else {
                statusRepository.findAllById(snippetIds).associateBy { it.snippetId }
            }

        return page.map { snippet ->
            val snippetStatus = statusMap[snippet.id] ?: SnippetStatus(snippetId = snippet.id)
            val content = snippetStore.get(snippet.id) ?: snippet.content
            mapToResponse(snippet, snippetStatus, content)
        }
    }

    private fun getSharedSnippetIds(userId: String): Set<UUID> =
        try {
            permissionClient
                .getUserPermissions(userId)
                .filter { it.level != PermissionLevel.NONE && it.level != PermissionLevel.OWNER }
                .map { it.snippetId }
                .toSet()
        } catch (_: Exception) {
            emptySet()
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

        assertIsOwner(snippet, ownerId)
        snippetRepository.delete(snippet)
        snippetStore.delete(id)
    }

    private fun assertCanEdit(
        snippet: Snippet,
        userId: String,
    ) {
        val permission = permissionClient.getPermission(snippet.id, userId)
        val canEdit = permission == PermissionLevel.OWNER || permission == PermissionLevel.WRITE
        if (!canEdit) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to edit this snippet")
        }
    }

    private fun assertIsOwner(
        snippet: Snippet,
        userId: String,
    ) {
        val permission = permissionClient.getPermission(snippet.id, userId)
        if (permission != PermissionLevel.OWNER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can delete this snippet")
        }
    }

    private fun assertCanView(
        snippet: Snippet,
        userId: String,
    ) {
        val permission = permissionClient.getPermission(snippet.id, userId)
        if (permission == PermissionLevel.NONE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view this snippet")
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
        snippetTestRunner?.runTestsQuietly(snippet.id, newContent, snippet.version)
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
