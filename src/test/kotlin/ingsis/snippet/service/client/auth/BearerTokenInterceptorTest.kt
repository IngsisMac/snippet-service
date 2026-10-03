package ingsis.snippet.service.client.auth

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class BearerTokenInterceptorTest {
    private lateinit var builder: RestClient.Builder
    private lateinit var mockServer: MockRestServiceServer

    @BeforeEach
    fun setUp() {
        builder = RestClient.builder().baseUrl("http://permission-service:8081")
        mockServer = MockRestServiceServer.bindTo(builder).build()
    }

    @Test
    fun shouldAttachBearerTokenWhenProviderHasOne() {
        val client = builder.requestInterceptor(BearerTokenInterceptor { "m2m-token" }).build()
        mockServer
            .expect(requestTo("http://permission-service:8081/internal/permissions/user/u1"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer m2m-token"))
            .andRespond(withSuccess())

        client
            .get()
            .uri("/internal/permissions/user/u1")
            .retrieve()
            .toBodilessEntity()

        mockServer.verify()
    }

    @Test
    fun shouldLeaveRequestUntouchedWhenServiceAuthenticationIsDisabled() {
        val client = builder.requestInterceptor(BearerTokenInterceptor(ServiceTokenProvider.NONE)).build()
        mockServer
            .expect(requestTo("http://permission-service:8081/internal/permissions/user/u1"))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withSuccess())

        client
            .get()
            .uri("/internal/permissions/user/u1")
            .retrieve()
            .toBodilessEntity()

        mockServer.verify()
    }
}
