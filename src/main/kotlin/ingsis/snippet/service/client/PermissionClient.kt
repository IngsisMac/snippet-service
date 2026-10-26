package ingsis.snippet.service.client

import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.client.dto.UserPermissionResponse
import ingsis.snippet.service.client.dto.UserRulesResponse
import java.util.UUID

interface PermissionClient {
    fun assignOwner(
        snippetId: UUID,
        userId: String,
    )

    fun deletePermissions(snippetId: UUID)

    fun getPermission(
        snippetId: UUID,
        userId: String,
    ): PermissionLevel

    fun getLintRules(userId: String): Map<String, Any> = getLintRuleSet(userId).rules

    /** Reglas de lint del usuario junto con su `rulesVersion`, para publicar pedidos de linteo. */
    fun getLintRuleSet(userId: String): UserRulesResponse

    fun getFormatRules(userId: String): Map<String, Any>

    fun getUserPermissions(
        userId: String,
        level: PermissionLevel? = null,
    ): List<UserPermissionResponse>
}
