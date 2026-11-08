package ingsis.snippet.service.controller.dto

import ingsis.snippet.service.client.dto.LintFindingClientDto
import ingsis.snippet.service.domain.model.ComplianceStatus
import java.time.Instant
import java.util.UUID

/**
 * `content` y `findings` solo viajan en el detalle (`GET /api/snippets/{id}`): el listado los
 * omite para no pedir el contenido al asset-service ni deserializar hallazgos por cada fila.
 */
data class SnippetResponse(
    val id: UUID,
    val name: String,
    val ownerId: String,
    val language: String,
    val version: String,
    val description: String? = null,
    val content: String? = null,
    val status: ComplianceStatus,
    val rulesVersion: Int,
    val findingsCount: Int,
    val findings: List<LintFindingClientDto>? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
