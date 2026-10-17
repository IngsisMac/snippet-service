package ingsis.snippet.service.redis

import ingsis.snippet.redis.event.ComplianceResultEvent
import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import org.austral.ingsis.redis.RedisStreamConsumer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.stream.ObjectRecord
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.stream.StreamReceiver.StreamReceiverOptions
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
@Profile("!test")
class ComplianceResultConsumer(
    private val statusRepository: SnippetStatusRepository,
    redisTemplate: RedisTemplate<String, String>,
) : RedisStreamConsumer<ComplianceResultEvent>(
        streamKey = "lint-result",
        groupId = "snippet-compliance-group",
        redis = redisTemplate,
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)

    public override fun options(): StreamReceiverOptions<String, ObjectRecord<String, ComplianceResultEvent>> =
        StreamReceiverOptions
            .builder()
            .pollTimeout(Duration.ofSeconds(POLL_TIMEOUT_SECONDS))
            .targetType(ComplianceResultEvent::class.java)
            .build()

    @Transactional
    @Suppress("TooGenericExceptionCaught")
    public override fun onMessage(record: ObjectRecord<String, ComplianceResultEvent>) {
        try {
            val event = record.value
            val snippetId = parseSnippetId(event.snippetId) ?: return

            val current = statusRepository.findById(snippetId).orElse(SnippetStatus(snippetId = snippetId))

            if (event.rulesVersion < current.rulesVersion) {
                logger.warn(
                    "Discarding obsolete result v{} for snippet {} (current v{})",
                    event.rulesVersion,
                    event.snippetId,
                    current.rulesVersion,
                )
                return
            }

            current.rulesVersion = event.rulesVersion
            current.status = parseStatus(event.status)
            current.findingsCount = event.findingsCount
            current.lastCheckedAt = Instant.now()
            statusRepository.save(current)
            logger.info("Updated compliance status for snippet {} to {}", current.snippetId, current.status)
        } catch (t: Throwable) {
            logger.error("Unhandled error processing lint-result record", t)
        }
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

    companion object {
        private const val POLL_TIMEOUT_SECONDS = 10L
    }
}
