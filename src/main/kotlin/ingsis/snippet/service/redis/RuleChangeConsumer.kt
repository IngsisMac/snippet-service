package ingsis.snippet.service.redis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import ingsis.snippet.service.store.SnippetStore
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Fan-out del cambio de reglas de un usuario: un mensaje de lint por cada snippet del que es
 * `OWNER` según `permission-service`. Vive acá y no en `permission-service` porque este
 * servicio es el que conoce versión y contenido de cada snippet.
 */
@Component
class RuleChangeConsumer(
    private val snippetRepository: SnippetRepository,
    private val statusRepository: SnippetStatusRepository,
    private val snippetStore: SnippetStore,
    private val permissionClient: PermissionClient,
    private val lintRequestPublisher: LintRequestPublisher,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processLintRuleChange(record: MapRecord<String, String, String>) {
        val valueMap = record.value
        val userId = valueMap["userId"] ?: return
        val rulesVersion = valueMap["rulesVersion"]?.toIntOrNull() ?: 0
        val rules = parseRules(valueMap["rules"] ?: "{}")

        val ownedIds =
            permissionClient
                .getUserPermissions(userId, PermissionLevel.OWNER)
                .map { it.snippetId }
        val snippets = snippetRepository.findAllById(ownedIds)
        logger.info(
            "Processing lint rule change for user {} v{} across {} snippets",
            userId,
            rulesVersion,
            snippets.size,
        )

        for (snippet in snippets) {
            processSnippetLint(snippet, rulesVersion, rules)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processSnippetLint(
        snippet: Snippet,
        rulesVersion: Int,
        rules: Map<String, Any>,
    ) {
        try {
            val content = snippetStore.get(snippet.id)
            if (content == null) {
                logger.warn("Snippet {} has no content in storage; marking as FAILED", snippet.id)
                updateStatus(snippet, rulesVersion, ComplianceStatus.FAILED)
                return
            }
            updateStatus(snippet, rulesVersion, ComplianceStatus.PENDING)
            lintRequestPublisher.publishLintRequest(snippet.id, content, snippet.version, rulesVersion, rules)
        } catch (ex: Exception) {
            logger.error("Failed to process lint for snippet {}: {}", snippet.id, ex.message, ex)
        }
    }

    private fun updateStatus(
        snippet: Snippet,
        rulesVersion: Int,
        newStatus: ComplianceStatus,
    ) {
        val status = statusRepository.findById(snippet.id).orElse(SnippetStatus(snippetId = snippet.id))
        status.rulesVersion = rulesVersion
        status.status = newStatus
        status.lastCheckedAt = Instant.now()
        statusRepository.save(status)
    }

    private fun parseRules(rulesJson: String): Map<String, Any> =
        try {
            objectMapper.readValue(rulesJson, object : TypeReference<Map<String, Any>>() {})
        } catch (_: Exception) {
            emptyMap()
        }
}
