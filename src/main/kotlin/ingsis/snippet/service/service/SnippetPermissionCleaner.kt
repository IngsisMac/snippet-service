package ingsis.snippet.service.service

import ingsis.snippet.service.client.PermissionClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Limpia las filas de ownership y permisos de un snippet borrado (Bug 3).
 *
 * Corre AFTER_COMMIT del deleteSnippet en snippet-service. Si permission-service falla o no
 * responde, se registra un WARN sin hacer fallar el borrado del snippet (consistencia eventual).
 */
@Component
class SnippetPermissionCleaner(
    private val permissionClient: PermissionClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Suppress("TooGenericExceptionCaught")
    fun onSnippetDeleted(event: SnippetDeletedEvent) {
        try {
            permissionClient.deletePermissions(event.snippetId)
            logger.info("Deleted permissions for snippet {}", event.snippetId)
        } catch (ex: Exception) {
            logger.warn(
                "Could not delete permissions for snippet {}: {}",
                event.snippetId,
                ex.message,
            )
        }
    }
}
