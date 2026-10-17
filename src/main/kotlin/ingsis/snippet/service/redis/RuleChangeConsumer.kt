package ingsis.snippet.service.redis

import ingsis.snippet.redis.event.LintRequestEvent
import ingsis.snippet.redis.event.LintRulesChangeEvent
import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import ingsis.snippet.service.store.SnippetStore
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
class RuleChangeConsumer(
    private val snippetRepository: SnippetRepository,
    private val statusRepository: SnippetStatusRepository,
    private val snippetStore: SnippetStore,
    private val permissionClient: PermissionClient,
    private val lintRequestProducer: LintRequestProducer,
    redisTemplate: RedisTemplate<String, String>,
) : RedisStreamConsumer<LintRulesChangeEvent>(
        streamKey = "lint-rules",
        groupId = "snippet-lint-rules-group",
        redis = redisTemplate,
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)

    public override fun options(): StreamReceiverOptions<String, ObjectRecord<String, LintRulesChangeEvent>> =
        StreamReceiverOptions
            .builder()
            .pollTimeout(Duration.ofSeconds(POLL_TIMEOUT_SECONDS))
            .targetType(LintRulesChangeEvent::class.java)
            .build()

    @Transactional
    @Suppress("TooGenericExceptionCaught")
    public override fun onMessage(record: ObjectRecord<String, LintRulesChangeEvent>) {
        try {
            handleRuleChange(record.value)
        } catch (t: Throwable) {
            logger.error("Unhandled error processing lint-rules record", t)
        }
    }

    private fun handleRuleChange(event: LintRulesChangeEvent) {
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
            "Processing lint rule change for user {} v{} across {} snippets",
            userId,
            event.rulesVersion,
            snippets.size,
        )

        for (snippet in snippets) {
            processSnippetLint(snippet, event.rulesVersion, event.rules)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processSnippetLint(
        snippet: Snippet,
        rulesVersion: Int,
        rules: String,
    ) {
        try {
            updateStatus(snippet.id, rulesVersion, ComplianceStatus.PENDING)
            val content = fetchContentWithRetry(snippet.id)
            if (content == null) {
                logger.warn("Snippet {} has no content in storage; marking as FAILED", snippet.id)
                updateStatus(snippet.id, rulesVersion, ComplianceStatus.FAILED)
                return
            }
            val request =
                LintRequestEvent(
                    snippetId = snippet.id.toString(),
                    content = content,
                    version = snippet.version,
                    rulesVersion = rulesVersion,
                    rules = rules,
                )
            lintRequestProducer.emit(request)
        } catch (ex: Exception) {
            logger.error("Failed to process lint for snippet {}: {}", snippet.id, ex.message, ex)
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

    private fun updateStatus(
        snippetId: UUID,
        rulesVersion: Int,
        newStatus: ComplianceStatus,
    ) {
        val status = statusRepository.findById(snippetId).orElse(SnippetStatus(snippetId = snippetId))
        status.rulesVersion = rulesVersion
        status.status = newStatus
        status.lastCheckedAt = Instant.now()
        statusRepository.save(status)
    }

    companion object {
        private const val POLL_TIMEOUT_SECONDS = 10L
        private const val MAX_RETRIES = 3
    }
}
