package ingsis.snippet.service.service

import ingsis.snippet.service.client.RunnerExecutionClient
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.store.SnippetStore
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.io.InputStream
import java.util.UUID

/**
 * Ejecución interactiva de un snippet (US #9 y #10).
 *
 * `snippet-service` resuelve el permiso y carga el contenido; la sesión en sí vive en el runner,
 * que es el servicio que tiene el intérprete. Acá no queda estado: el `executionId` es un UUID que
 * devuelve el runner, y cada operación sobre él vuelve a exigir permiso de lectura sobre el
 * snippet. No se registra qué usuario abrió cada ejecución: hacerlo agregaría estado en memoria a
 * un servicio que hoy no tiene ninguno, y el id es impredecible y solo sirve a quien ya puede leer
 * el snippet.
 */
@Service
class SnippetExecutionService(
    private val snippetRepository: SnippetRepository,
    private val snippetStore: SnippetStore,
    private val access: SnippetAccess,
    private val executionClient: RunnerExecutionClient,
) {
    @Transactional(readOnly = true)
    fun start(
        snippetId: UUID,
        userId: String,
        env: Map<String, String>,
    ): UUID {
        access.requireView(snippetId, userId)
        val snippet =
            snippetRepository
                .findById(snippetId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found") }
        val content =
            snippetStore.get(snippetId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet content not found in storage")
        return executionClient.start(content, snippet.version, env)
    }

    fun stream(
        snippetId: UUID,
        userId: String,
        executionId: UUID,
        sink: (InputStream) -> Unit,
    ) {
        access.requireView(snippetId, userId)
        // Antes de empezar a escribir: después de mandar el primer byte el código de respuesta
        // ya está comprometido y un id inexistente terminaría en 500 en vez de 404.
        executionClient.requireExists(executionId)
        executionClient.stream(executionId, sink)
    }

    fun sendInput(
        snippetId: UUID,
        userId: String,
        executionId: UUID,
        value: String,
    ) {
        access.requireView(snippetId, userId)
        executionClient.sendInput(executionId, value)
    }

    fun cancel(
        snippetId: UUID,
        userId: String,
        executionId: UUID,
    ) {
        access.requireView(snippetId, userId)
        executionClient.cancel(executionId)
    }
}
