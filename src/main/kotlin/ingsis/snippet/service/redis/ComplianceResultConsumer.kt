package ingsis.snippet.service.redis

import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Component
class ComplianceResultConsumer(
    private val statusRepository: SnippetStatusRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processResult(record: MapRecord<String, String, String>) {
        val valueMap = record.value
        val snippetId = parseSnippetId(valueMap["snippetId"]) ?: return
        val rulesVersion = valueMap["rulesVersion"]?.toIntOrNull() ?: 0
        val statusStr = valueMap["status"] ?: "PENDING"
        val findingsCount = valueMap["findingsCount"]?.toIntOrNull() ?: 0

        val current = statusRepository.findById(snippetId).orElse(SnippetStatus(snippetId = snippetId))

        if (rulesVersion < current.rulesVersion) {
            logger.warn(
                "Discarding obsolete result v{} for snippet {} (current v{})",
                rulesVersion,
                snippetId,
                current.rulesVersion
            )
            return
        }

        updateStatus(current, rulesVersion, statusStr, findingsCount)
    }

    private fun updateStatus(
        status: SnippetStatus,
        rulesVersion: Int,
        statusStr: String,
        findingsCount: Int,
    ) {
        status.rulesVersion = rulesVersion
        status.status = parseStatus(statusStr)
        status.findingsCount = findingsCount
        status.lastCheckedAt = Instant.now()
        statusRepository.save(status)
        logger.info("Updated compliance status for snippet {} to {}", status.snippetId, status.status)
    }

    private fun parseSnippetId(idStr: String?): UUID? =
        try {
            idStr?.let { UUID.fromString(it) }
        } catch (_: Exception) {
            null
        }

    private fun parseStatus(statusStr: String): ComplianceStatus =
        try {
            ComplianceStatus.valueOf(statusStr)
        } catch (_: Exception) {
            ComplianceStatus.FAILED
        }
}
