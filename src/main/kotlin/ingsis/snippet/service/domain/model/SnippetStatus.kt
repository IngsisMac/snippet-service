package ingsis.snippet.service.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "snippet_statuses")
class SnippetStatus(
    @Id
    @Column(name = "snippet_id")
    val snippetId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ComplianceStatus = ComplianceStatus.PENDING,
    @Column(name = "rules_version", nullable = false)
    var rulesVersion: Int = 0,
    @Column(name = "findings_count", nullable = false)
    var findingsCount: Int = 0,
    @Column(name = "last_checked_at", nullable = false)
    var lastCheckedAt: Instant = Instant.now(),
)
