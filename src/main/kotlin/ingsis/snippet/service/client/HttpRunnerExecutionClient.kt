package ingsis.snippet.service.client

import ingsis.snippet.service.client.dto.ExecutionInputClientRequest
import ingsis.snippet.service.client.dto.StartExecutionClientRequest
import ingsis.snippet.service.client.dto.StartExecutionClientResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.io.InputStream
import java.util.UUID

/**
 * Los errores del runner se traducen al mismo código hacia afuera: sin esto, un `executionId`
 * inexistente llega como `HttpClientErrorException`, que Spring convierte en `500`, y el cliente
 * no puede distinguir "esa ejecución no existe" de "el runner se cayó".
 */
class HttpRunnerExecutionClient(
    private val restClient: RestClient,
    private val streamingClient: RestClient,
) : RunnerExecutionClient {
    override fun start(
        content: String,
        version: String,
        env: Map<String, String>,
    ): UUID =
        translatingErrors {
            restClient
                .post()
                .uri("/runner/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(StartExecutionClientRequest(content, version, env))
                .retrieve()
                .body(StartExecutionClientResponse::class.java)
                ?.executionId
                ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Runner did not return an execution id")
        }

    override fun requireExists(executionId: UUID) =
        translatingErrors {
            restClient
                .get()
                .uri("/runner/executions/{id}", executionId)
                .retrieve()
                .toBodilessEntity()
            Unit
        }

    override fun stream(
        executionId: UUID,
        sink: (InputStream) -> Unit,
    ) = translatingErrors {
        streamingClient
            .get()
            .uri("/runner/executions/{id}/stream", executionId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange({ _, response ->
                sink(response.body)
            }, false)
        Unit
    }

    override fun sendInput(
        executionId: UUID,
        value: String,
    ) = translatingErrors {
        restClient
            .post()
            .uri("/runner/executions/{id}/input", executionId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ExecutionInputClientRequest(value))
            .retrieve()
            .toBodilessEntity()
        Unit
    }

    override fun cancel(executionId: UUID) =
        translatingErrors {
            restClient
                .delete()
                .uri("/runner/executions/{id}", executionId)
                .retrieve()
                .toBodilessEntity()
            Unit
        }

    private fun <T> translatingErrors(call: () -> T): T =
        try {
            call()
        } catch (ex: HttpStatusCodeException) {
            throw ResponseStatusException(ex.statusCode, reasonFor(ex.statusCode.value()), ex)
        }

    private fun reasonFor(status: Int): String =
        when (status) {
            HttpStatus.NOT_FOUND.value() -> "Execution not found"
            HttpStatus.TOO_MANY_REQUESTS.value() -> "Too many executions in progress"
            HttpStatus.CONFLICT.value() -> "Execution already finished"
            else -> "The runner rejected the execution request"
        }
}
