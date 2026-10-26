package ingsis.snippet.service.client.fake

import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.client.dto.UserPermissionResponse
import ingsis.snippet.service.client.dto.UserRulesResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FakePermissionClient : PermissionClient {
    private val permissions = ConcurrentHashMap<UUID, MutableMap<String, PermissionLevel>>()

    override fun assignOwner(
        snippetId: UUID,
        userId: String,
    ) {
        val snippetMap = permissions.computeIfAbsent(snippetId) { ConcurrentHashMap() }
        snippetMap[userId] = PermissionLevel.OWNER
    }

    override fun deletePermissions(snippetId: UUID) {
        permissions.remove(snippetId)
    }

    override fun getPermission(
        snippetId: UUID,
        userId: String,
    ): PermissionLevel = permissions[snippetId]?.get(userId) ?: PermissionLevel.NONE

    fun setPermission(
        snippetId: UUID,
        userId: String,
        level: PermissionLevel,
    ) {
        val snippetMap = permissions.computeIfAbsent(snippetId) { ConcurrentHashMap() }
        snippetMap[userId] = level
    }

    private val lintRules = ConcurrentHashMap<String, UserRulesResponse>()
    private val formatRules = ConcurrentHashMap<String, Map<String, Any>>()
    var lintRulesFailure: RuntimeException? = null

    fun setLintRules(
        userId: String,
        rules: Map<String, Any>,
        rulesVersion: Int = 1,
    ) {
        lintRules[userId] = UserRulesResponse(userId = userId, rulesVersion = rulesVersion, rules = rules)
    }

    fun setFormatRules(
        userId: String,
        rules: Map<String, Any>,
    ) {
        formatRules[userId] = rules
    }

    override fun getLintRuleSet(userId: String): UserRulesResponse {
        lintRulesFailure?.let { throw it }
        return lintRules[userId] ?: UserRulesResponse(userId = userId, rulesVersion = 0)
    }

    override fun getFormatRules(userId: String): Map<String, Any> = formatRules[userId] ?: emptyMap()

    override fun getUserPermissions(
        userId: String,
        level: PermissionLevel?,
    ): List<UserPermissionResponse> {
        val result = mutableListOf<UserPermissionResponse>()
        permissions.forEach { (snippetId, userMap) ->
            val userLevel = userMap[userId]
            if (userLevel != null && userLevel != PermissionLevel.NONE) {
                if (level == null || userLevel == level) {
                    result.add(
                        UserPermissionResponse(
                            snippetId = snippetId,
                            userId = userId,
                            level = userLevel,
                        ),
                    )
                }
            }
        }
        return result
    }

    fun clear() {
        permissions.clear()
        lintRules.clear()
        formatRules.clear()
    }
}
