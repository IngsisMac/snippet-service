package ingsis.snippet.service.client.auth

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Obtiene un access token de Auth0 con el flujo `client_credentials` y lo cachea hasta poco
 * antes de su expiración. Se eligió una implementación explícita sobre `RestClient` en lugar
 * de `spring-security-oauth2-client` porque son dos llamadas (pedir y cachear) y así el flujo
 * queda visible y testeable sin configuración adicional de Spring Security.
 */
class Auth0ClientCredentialsTokenProvider(
    private val restClient: RestClient,
    private val properties: M2mProperties,
    private val clock: Clock = Clock.systemUTC(),
    private val refreshMargin: Duration = DEFAULT_REFRESH_MARGIN,
) : ServiceTokenProvider {
    @Volatile
    private var cached: CachedToken? = null

    override fun token(): String {
        val current = cached
        if (current != null && current.expiresAt.isAfter(clock.instant().plus(refreshMargin))) {
            return current.value
        }
        return synchronized(this) {
            val again = cached
            if (again != null && again.expiresAt.isAfter(clock.instant().plus(refreshMargin))) {
                again.value
            } else {
                requestToken().also { cached = it }.value
            }
        }
    }

    private fun requestToken(): CachedToken {
        val response =
            restClient
                .post()
                .uri(properties.tokenUri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    TokenRequest(
                        clientId = properties.clientId,
                        clientSecret = properties.clientSecret,
                        audience = properties.audience,
                    ),
                ).retrieve()
                .body(TokenResponse::class.java)
                ?: error("Empty token response from Auth0")
        return CachedToken(
            value = response.accessToken,
            expiresAt = clock.instant().plusSeconds(response.expiresIn),
        )
    }

    private data class CachedToken(
        val value: String,
        val expiresAt: Instant,
    )

    companion object {
        val DEFAULT_REFRESH_MARGIN: Duration = Duration.ofSeconds(60)
    }
}

data class TokenRequest(
    @JsonProperty("client_id") val clientId: String,
    @JsonProperty("client_secret") val clientSecret: String,
    @JsonProperty("audience") val audience: String,
    @JsonProperty("grant_type") val grantType: String = "client_credentials",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("token_type") val tokenType: String = "Bearer",
)
