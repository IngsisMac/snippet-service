package ingsis.snippet.service.client.fake

import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.client.dto.PermissionLevel
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

    fun clear() {
        permissions.clear()
    }
}
