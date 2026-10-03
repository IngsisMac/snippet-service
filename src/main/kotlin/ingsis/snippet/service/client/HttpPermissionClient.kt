package ingsis.snippet.service.client

import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.client.dto.UserPermissionResponse
import ingsis.snippet.service.client.dto.UserRulesResponse
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
                    .path("/internal/permissions")
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
                        .path("/internal/permissions/{snippetId}")
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

    override fun getLintRules(userId: String): Map<String, Any> {
        val response =
            restClient
                .get()
                .uri("/internal/rules/lint/{userId}", userId)
                .retrieve()
                .body(UserRulesResponse::class.java)
        return response?.rules ?: emptyMap()
    }

    override fun getFormatRules(userId: String): Map<String, Any> {
        val response =
            restClient
                .get()
                .uri("/internal/rules/format/{userId}", userId)
                .retrieve()
                .body(UserRulesResponse::class.java)
        return response?.rules ?: emptyMap()
    }

    override fun getUserPermissions(
        userId: String,
        level: PermissionLevel?,
    ): List<UserPermissionResponse> {
        val responseType = object : ParameterizedTypeReference<List<UserPermissionResponse>>() {}
        val response =
            restClient
                .get()
                .uri { builder ->
                    builder.path("/internal/permissions/user/{userId}")
                    level?.let { builder.queryParam("level", it.name) }
                    builder.build(userId)
                }.retrieve()
                .body(responseType)
        return response ?: emptyList()
    }
}
