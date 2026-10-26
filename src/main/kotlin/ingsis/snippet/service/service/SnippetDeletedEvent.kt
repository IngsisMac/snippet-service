package ingsis.snippet.service.service

import java.util.UUID

/**
 * Evento emitido cuando un snippet es eliminado en [SnippetService.deleteSnippet].
 * Consumido AFTER_COMMIT por [SnippetPermissionCleaner] para revocar los permisos en permission-service.
 */
data class SnippetDeletedEvent(
    val snippetId: UUID,
)
