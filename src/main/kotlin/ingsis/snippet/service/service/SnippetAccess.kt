package ingsis.snippet.service.service

import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.client.dto.PermissionLevel
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Único lugar donde se decide si un usuario puede ver, editar o borrar un snippet. La respuesta
 * siempre sale de `permission-service`: `owner_id` registra quién lo creó y no otorga permisos por
 * sí mismo, así una transferencia de ownership tiene efecto inmediato.
 */
@Component
class SnippetAccess(
    private val permissionClient: PermissionClient,
) {
    /** Devuelve el nivel del usuario, que ya se consultó para decidir y el detalle lo reutiliza. */
    fun requireView(
        snippetId: UUID,
        userId: String,
    ): PermissionLevel {
        val permission = permissionClient.getPermission(snippetId, userId)
        if (permission == PermissionLevel.NONE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view this snippet")
        }
        return permission
    }

    fun requireEdit(
        snippetId: UUID,
        userId: String,
    ): PermissionLevel {
        val permission = permissionClient.getPermission(snippetId, userId)
        if (permission != PermissionLevel.OWNER && permission != PermissionLevel.WRITE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to edit this snippet")
        }
        return permission
    }

    fun requireOwner(
        snippetId: UUID,
        userId: String,
    ) {
        if (permissionClient.getPermission(snippetId, userId) != PermissionLevel.OWNER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can delete this snippet")
        }
    }
}
