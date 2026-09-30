package ingsis.snippet.service.client.dto

import java.time.Instant
import java.util.UUID

enum class PermissionLevel {
    OWNER,
    WRITE,
    READ,
    NONE,
}

data class PermissionCheckResponse(
    val level: PermissionLevel,
)

data class AssignOwnerResponse(
    val id: UUID,
    val snippetId: UUID,
    val userId: String,
    val permission: PermissionLevel,
    val createdAt: Instant,
)

data class UserRulesResponse(
    val userId: String,
    val rulesVersion: Int = 1,
    val rules: Map<String, Any> = emptyMap(),
)
