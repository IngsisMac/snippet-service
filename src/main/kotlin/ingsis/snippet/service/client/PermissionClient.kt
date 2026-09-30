package ingsis.snippet.service.client

import ingsis.snippet.service.client.dto.PermissionLevel
import java.util.UUID

interface PermissionClient {
    fun assignOwner(
        snippetId: UUID,
        userId: String,
    )

    fun getPermission(
        snippetId: UUID,
        userId: String,
    ): PermissionLevel

    fun getLintRules(userId: String): Map<String, Any>

    fun getFormatRules(userId: String): Map<String, Any>
}
