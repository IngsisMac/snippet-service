package ingsis.snippet.service.client

import java.io.InputStream
import java.util.UUID

/**
 * Sesiones de ejecución interactiva en `runner-service` (US #9 y #10). Está aparte de
 * [RunnerClient] porque necesita otro cliente HTTP: el stream de salida puede quedar abierto
 * minutos esperando que una persona escriba, y el `read-timeout` de 5 s del resto de las llamadas
 * lo cortaría.
 */
interface RunnerExecutionClient {
    fun start(
        content: String,
        version: String,
        env: Map<String, String>,
    ): UUID

    /** Falla con `404` si la ejecución no existe. Se consulta antes de abrir el stream. */
    fun requireExists(executionId: UUID)

    /** Abre el stream SSE y se lo entrega a [sink]. La conexión se cierra al volver. */
    fun stream(
        executionId: UUID,
        sink: (InputStream) -> Unit,
    )

    fun sendInput(
        executionId: UUID,
        value: String,
    )

    fun cancel(executionId: UUID)
}
