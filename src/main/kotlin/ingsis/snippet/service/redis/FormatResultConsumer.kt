package ingsis.snippet.service.redis

import ingsis.snippet.redis.event.FormatResultEvent
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import ingsis.snippet.service.store.SnippetStore
import org.austral.ingsis.redis.RedisStreamConsumer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.stream.ObjectRecord
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.stream.StreamReceiver.StreamReceiverOptions
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
@Profile("!test")
class FormatResultConsumer(
    private val statusRepository: SnippetStatusRepository,
    private val snippetStore: SnippetStore,
    redisTemplate: RedisTemplate<String, String>,
    transactions: TransactionTemplate,
    private val work: StreamWorkQueue = StreamWorkQueue("format-result", transactions),
) : RedisStreamConsumer<FormatResultEvent>(
        streamKey = "format-result",
        groupId = "snippet-format-result-group",
        redis = redisTemplate,
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)

    public override fun options(): StreamReceiverOptions<String, ObjectRecord<String, FormatResultEvent>> =
        StreamReceiverOptions
            .builder()
            .pollTimeout(Duration.ofSeconds(POLL_TIMEOUT_SECONDS))
            .targetType(FormatResultEvent::class.java)
            .build()

    public override fun onMessage(record: ObjectRecord<String, FormatResultEvent>) {
        val event = record.value
        work.submit {
            val cid = event.correlationId.ifBlank { UUID.randomUUID().toString() }
            org.slf4j.MDC.put(ingsis.snippet.service.tracing.CorrelationIdFilter.CORRELATION_ID_KEY, cid)
            try {
                processFormatResult(event)
            } finally {
                org.slf4j.MDC.remove(ingsis.snippet.service.tracing.CorrelationIdFilter.CORRELATION_ID_KEY)
            }
        }
    }

    private fun processFormatResult(event: FormatResultEvent) {
        val snippetId = parseSnippetId(event.snippetId) ?: return
        val current = statusRepository.findById(snippetId).orElse(SnippetStatus(snippetId = snippetId))

        if (event.rulesVersion < current.formatRulesVersion) {
            logger.warn(
                "Discarding obsolete format result v{} for snippet {} (current v{})",
                event.rulesVersion,
                event.snippetId,
                current.formatRulesVersion,
            )
            return
        }

        if (event.status != "SUCCESS" || event.formattedContent.isBlank()) {
            logger.warn("Format failed or empty for snippet {}; preserving existing content in store", snippetId)
            saveStatus(current, event.rulesVersion)
            return
        }

        snippetStore.put(snippetId, event.formattedContent)
        saveStatus(current, event.rulesVersion)
        logger.info("Updated formatted content and format rules version for snippet {}", snippetId)
    }

    private fun saveStatus(
        status: SnippetStatus,
        rulesVersion: Int,
    ) {
        status.formatRulesVersion = rulesVersion
        status.lastCheckedAt = Instant.now()
        statusRepository.save(status)
    }

    private fun parseSnippetId(idStr: String?): UUID? =
        try {
            idStr?.let { UUID.fromString(it) }
        } catch (_: Exception) {
            null
        }

    companion object {
        private const val POLL_TIMEOUT_SECONDS = 10L
    }
}
