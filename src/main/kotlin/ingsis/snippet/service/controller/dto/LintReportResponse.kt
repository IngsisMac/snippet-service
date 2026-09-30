package ingsis.snippet.service.controller.dto

import ingsis.snippet.service.client.dto.LintFindingClientDto
import ingsis.snippet.service.domain.model.ComplianceStatus
import java.util.UUID

data class LintReportResponse(
    val snippetId: UUID,
    val status: ComplianceStatus,
    val findings: List<LintFindingClientDto>,
    val findingsCount: Int,
)
