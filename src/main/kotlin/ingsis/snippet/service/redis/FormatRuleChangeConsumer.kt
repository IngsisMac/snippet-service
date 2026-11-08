package ingsis.snippet.service.redis

import ingsis.snippet.redis.event.FormatRequestEvent
import ingsis.snippet.redis.event.FormatRulesChangeEvent
import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.store.SnippetStore
import org.austral.ingsis.redis.RedisStreamConsumer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.stream.ObjectRecord
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.stream.StreamReceiver.StreamReceiverOptions
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
@Profile("!test")
class FormatRuleChangeConsumer(
    private val snippetRepository: SnippetRepository,
    private val snippetStore: SnippetStore,
    private val permissionClient: PermissionClient,
    private val formatRequestProducer: FormatRequestProducer,
    redisTemplate: RedisTemplate<String, String>,
    private val work: StreamWorkQueue = StreamWorkQueue("format-rules"),
) : RedisStreamConsumer<FormatRulesChangeEvent>(
        streamKey = "format-rules",
        groupId = "snippet-format-rules-group",
        redis = redisTemplate,
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)

    public override fun options(): StreamReceiverOptions<String, ObjectRecord<String, FormatRulesChangeEvent>> =
        StreamReceiverOptions
            .builder()
            .pollTimeout(Duration.ofSeconds(POLL_TIMEOUT_SECONDS))
            .targetType(FormatRulesChangeEvent::class.java)
            .build()

    public override fun onMessage(record: ObjectRecord<String, FormatRulesChangeEvent>) {
        val event = record.value
        work.submit {
            val cid = event.correlationId.ifBlank { UUID.randomUUID().toString() }
            org.slf4j.MDC.put(ingsis.snippet.service.tracing.CorrelationIdFilter.CORRELATION_ID_KEY, cid)
            try {
                handleFormatRuleChange(event)
            } finally {
                org.slf4j.MDC.remove(ingsis.snippet.service.tracing.CorrelationIdFilter.CORRELATION_ID_KEY)
            }
        }
    }

    private fun handleFormatRuleChange(event: FormatRulesChangeEvent) {
        val userId = event.userId
        if (userId.isBlank()) {
            return
        }

        val ownedIds =
            permissionClient
                .getUserPermissions(userId, PermissionLevel.OWNER)
                .map { it.snippetId }
        val snippets = snippetRepository.findAllById(ownedIds)
        logger.info(
            "Processing format rule change for user {} v{} across {} snippets",
            userId,
            event.rulesVersion,
            snippets.size,
        )

        for (snippet in snippets) {
            processSnippetFormat(snippet, event.rulesVersion, event.rules)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processSnippetFormat(
        snippet: Snippet,
        rulesVersion: Int,
        rules: String,
    ) {
        try {
            val content = fetchContentWithRetry(snippet.id)
            if (content == null) {
                logger.warn("Snippet {} has no content in storage; skipping format request", snippet.id)
                return
            }
            val request =
                FormatRequestEvent(
                    snippetId = snippet.id.toString(),
                    content = content,
                    version = snippet.version,
                    rulesVersion = rulesVersion,
                    rules = rules,
                    correlationId =
                        org.slf4j.MDC.get(ingsis.snippet.service.tracing.CorrelationIdFilter.CORRELATION_ID_KEY) ?: "",
                )
            formatRequestProducer.emit(request)
        } catch (ex: Exception) {
            logger.error("Failed to process format for snippet {}: {}", snippet.id, ex.message, ex)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun fetchContentWithRetry(snippetId: UUID): String? {
        for (attempt in 1..MAX_RETRIES) {
            try {
                val content = snippetStore.get(snippetId)
                if (content != null) {
                    return content
                }
            } catch (ex: Exception) {
                logger.warn("Attempt {} to fetch content for snippet {} failed: {}", attempt, snippetId, ex.message)
            }
        }
        return null
    }

    companion object {
        private const val POLL_TIMEOUT_SECONDS = 10L
        private const val MAX_RETRIES = 3
    }
}
