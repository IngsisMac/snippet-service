package ingsis.snippet.service.client

import ingsis.snippet.service.client.dto.PermissionLevel
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.client.RestClient
import java.util.UUID

class HttpPermissionClient(
    private val restClient: RestClient,
) : PermissionClient {
    override fun assignOwner(
        snippetId: UUID,
        userId: String,
    ) {
        restClient
            .post()
            .uri { builder ->
                builder
                    .path("/api/permissions")
                    .queryParam("snippetId", snippetId)
                    .queryParam("userId", userId)
                    .build()
            }.retrieve()
            .toBodilessEntity()
    }

    override fun getPermission(
        snippetId: UUID,
        userId: String,
    ): PermissionLevel {
        val responseType = object : ParameterizedTypeReference<Map<String, String>>() {}
        val response =
            restClient
                .get()
                .uri { builder ->
                    builder
                        .path("/api/permissions/{snippetId}")
                        .queryParam("user", userId)
                        .build(snippetId)
                }.retrieve()
                .body(responseType)

        val levelStr = response?.get("level") ?: return PermissionLevel.NONE
        return try {
            PermissionLevel.valueOf(levelStr.uppercase())
        } catch (_: IllegalArgumentException) {
            PermissionLevel.NONE
        }
    }
}
