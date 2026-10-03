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
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Toda decisión de acceso (ver, editar, borrar, listar) se resuelve contra `permission-service`
 * vía [PermissionClient]. La columna `owner_id` de `snippets` registra quién lo creó y nunca
 * otorga permisos por sí misma: así una transferencia de ownership hecha en permisos tiene
 * efecto inmediato acá, incluso en el listado de "mis snippets".
 *
 * El contenido vive solo en el asset-service detrás de [SnippetStore]; Postgres guarda metadata.
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
        userId: String,
    ): SnippetResponse {
        val snippet = findSnippet(id)
        assertCanView(snippet, userId)
        val status = statusRepository.findById(id).orElse(SnippetStatus(snippetId = id))
        return mapToResponse(snippet, status, snippetStore.get(id))
    }

    @Transactional(readOnly = true)
    fun getSnippetContent(
        id: UUID,
        userId: String,
    ): String {
        val snippet = findSnippet(id)
        assertCanView(snippet, userId)
        return loadContent(id)
    }

    @Transactional(readOnly = true)
    fun formatSnippet(
        id: UUID,
        userId: String,
        content: String? = null,
    ): String {
        val snippet = findSnippet(id)
        assertCanView(snippet, userId)
        val rules = permissionClient.getFormatRules(userId)
        return runnerClient.format(content ?: loadContent(id), snippet.version, rules)
    }

    @Transactional
    fun lintSnippet(
        id: UUID,
        userId: String,
    ): LintReportResponse {
        val snippet = findSnippet(id)
        assertCanView(snippet, userId)
        val rules = permissionClient.getLintRules(userId)
        val result = runnerClient.lint(loadContent(id), snippet.version, rules)
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

    /**
     * El listado no incluye el contenido: traerlo obligaría a una llamada al asset-service por
     * fila. El detalle (`getSnippet`) sí lo trae.
     */
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
        val accessibleIds = accessibleSnippetIds(userId, scope)
        if (accessibleIds.isEmpty()) {
            return PageImpl(emptyList(), pageable, 0)
        }
        val page = snippetRepository.findAllByIdsAndFilters(accessibleIds, name, language, status, pageable)
        return enrichSnippetResponses(page)
    }

    private fun accessibleSnippetIds(
        userId: String,
        scope: SnippetScope,
    ): Set<UUID> {
        val permissions =
            try {
                permissionClient.getUserPermissions(userId)
            } catch (ex: RestClientException) {
                throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Permission service unavailable", ex)
            }
        return permissions
            .filter { permission ->
                when (scope) {
                    SnippetScope.OWNED -> permission.level == PermissionLevel.OWNER
                    SnippetScope.SHARED ->
                        permission.level == PermissionLevel.READ ||
                            permission.level == PermissionLevel.WRITE
                    SnippetScope.ALL -> permission.level != PermissionLevel.NONE
                }
            }.map { it.snippetId }
            .toSet()
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
            mapToResponse(snippet, snippetStatus, content = null)
        }
    }

    @Transactional
    fun updateSnippet(
        id: UUID,
        userId: String,
        request: UpdateSnippetRequest,
    ): SnippetResponse {
        val snippet = findSnippet(id)
        assertCanEdit(snippet, userId)

        request.name?.let { snippet.name = it }
        val currentContent = snippetStore.get(id)
        request.content?.let { newContent ->
            if (newContent != currentContent) {
                applyContentUpdate(snippet, newContent)
            }
        }
        snippet.updatedAt = Instant.now()
        val updated = snippetRepository.save(snippet)

        val status = statusRepository.findById(id).orElse(SnippetStatus(snippetId = id))
        return mapToResponse(updated, status, request.content ?: currentContent)
    }

    @Transactional
    fun deleteSnippet(
        id: UUID,
        userId: String,
    ) {
        val snippet = findSnippet(id)
        assertIsOwner(snippet, userId)
        snippetRepository.delete(snippet)
        snippetStore.delete(id)
    }

    private fun findSnippet(id: UUID): Snippet =
        snippetRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found")
        }

    private fun loadContent(id: UUID): String =
        snippetStore.get(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet content not found in storage")

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
        snippetStore.put(snippet.id, newContent)
        val status = statusRepository.findById(snippet.id).orElse(SnippetStatus(snippetId = snippet.id))
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
        content: String?,
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
