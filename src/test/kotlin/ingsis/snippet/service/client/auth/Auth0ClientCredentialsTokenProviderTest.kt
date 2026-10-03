package ingsis.snippet.service.client.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class Auth0ClientCredentialsTokenProviderTest {
    private lateinit var mockServer: MockRestServiceServer
    private lateinit var restClient: RestClient
    private lateinit var clock: MutableClock
    private lateinit var provider: Auth0ClientCredentialsTokenProvider

    private val properties =
        M2mProperties(
            enabled = true,
            tokenUri = "https://tenant.auth0.com/oauth/token",
            clientId = "m2m-client",
            clientSecret = "m2m-secret",
            audience = "https://snippet-searcher.ingsis/",
        )

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        mockServer = MockRestServiceServer.bindTo(builder).build()
        restClient = builder.build()
        clock = MutableClock(Instant.parse("2026-10-03T12:00:00Z"))
        provider = Auth0ClientCredentialsTokenProvider(restClient, properties, clock)
    }

    @Test
    fun shouldRequestClientCredentialsTokenWithAudience() {
        mockServer
            .expect(requestTo("https://tenant.auth0.com/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.grant_type").value("client_credentials"))
            .andExpect(jsonPath("$.client_id").value("m2m-client"))
            .andExpect(jsonPath("$.client_secret").value("m2m-secret"))
            .andExpect(jsonPath("$.audience").value("https://snippet-searcher.ingsis/"))
            .andRespond(withSuccess(tokenJson("first", 3600), MediaType.APPLICATION_JSON))

        val token = provider.token()

        assertEquals("first", token)
        mockServer.verify()
    }

    @Test
    fun shouldReuseCachedTokenWhileItIsStillValid() {
        mockServer
            .expect(ExpectedCount.once(), requestTo("https://tenant.auth0.com/oauth/token"))
            .andRespond(withSuccess(tokenJson("cached", 3600), MediaType.APPLICATION_JSON))

        provider.token()
        clock.advance(Duration.ofMinutes(30))
        val second = provider.token()

        assertEquals("cached", second)
        mockServer.verify()
    }

    @Test
    fun shouldRefreshTokenShortlyBeforeExpiration() {
        mockServer
            .expect(requestTo("https://tenant.auth0.com/oauth/token"))
            .andRespond(withSuccess(tokenJson("old", 120), MediaType.APPLICATION_JSON))
        mockServer
            .expect(requestTo("https://tenant.auth0.com/oauth/token"))
            .andRespond(withSuccess(tokenJson("fresh", 3600), MediaType.APPLICATION_JSON))

        provider.token()
        clock.advance(Duration.ofSeconds(90))
        val refreshed = provider.token()

        assertEquals("fresh", refreshed)
        mockServer.verify()
    }

    private fun tokenJson(
        value: String,
        expiresIn: Int,
    ): String = """{"access_token":"$value","expires_in":$expiresIn,"token_type":"Bearer"}"""

    private class MutableClock(
        private var now: Instant,
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId) = this

        override fun instant(): Instant = now

        fun advance(duration: Duration) {
            now = now.plus(duration)
        }
    }
}
