package ingsis.snippet.service.client.auth

import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

/** Adjunta el token M2M como `Authorization: Bearer` a cada llamada saliente, si hay uno. */
class BearerTokenInterceptor(
    private val tokenProvider: ServiceTokenProvider,
) : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        tokenProvider.token()?.let { request.headers.setBearerAuth(it) }
        return execution.execute(request, body)
    }
}
