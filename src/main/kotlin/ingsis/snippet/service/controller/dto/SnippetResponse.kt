package ingsis.snippet.service.controller.dto

import ingsis.snippet.service.domain.model.ComplianceStatus
import java.time.Instant
import java.util.UUID

data class SnippetResponse(
    val id: UUID,
    val name: String,
    val ownerId: String,
    val language: String,
    val version: String,
    val content: String = "",
    val status: ComplianceStatus,
    val rulesVersion: Int,
    val findingsCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
