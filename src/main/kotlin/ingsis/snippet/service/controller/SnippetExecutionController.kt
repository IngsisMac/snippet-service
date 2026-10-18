package ingsis.snippet.service.controller

import ingsis.snippet.service.controller.dto.ExecutionInputRequest
import ingsis.snippet.service.controller.dto.StartExecutionRequest
import ingsis.snippet.service.controller.dto.StartExecutionResponse
import ingsis.snippet.service.service.SnippetExecutionService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.util.UUID

/**
 * Ejecución interactiva de un snippet (US #9 y #10): se arranca una sesión, se escucha la salida
 * a medida que se produce y se mandan los inputs que el programa va pidiendo.
 *
 * El stream se reenvía tal cual viene del runner con un [StreamingResponseBody]: no hace falta
 * volver a parsear los eventos SSE para pasarlos de largo, y así los bytes salen hacia el
 * navegador apenas llegan.
 */
@RestController
@RequestMapping("/api/snippets/{snippetId}/executions")
class SnippetExecutionController(
    private val executionService: SnippetExecutionService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun start(
        @PathVariable snippetId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody(required = false) request: StartExecutionRequest?,
    ): StartExecutionResponse =
        StartExecutionResponse(executionService.start(snippetId, jwt.subject, request?.env ?: emptyMap()))

    @GetMapping("/{executionId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @PathVariable snippetId: UUID,
        @PathVariable executionId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<StreamingResponseBody> {
        val userId = jwt.subject
        val body =
            StreamingResponseBody { out ->
                executionService.stream(snippetId, userId, executionId) { upstream ->
                    // `copyTo` solo haría flush al final: con el buffer de 8 KB del servlet, todo
                    // el programa llegaría de golpe al cerrarse el stream y se perdería el "a
                    // medida que se evalúa" que pide la consigna. Hay que empujar cada bloque.
                    val buffer = ByteArray(STREAM_BUFFER_BYTES)
                    while (true) {
                        val read = upstream.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        out.flush()
                    }
                }
            }
        return ResponseEntity
            .ok()
            // Sin esto un proxy intermedio junta los eventos y el output deja de verse en vivo.
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .header("X-Accel-Buffering", "no")
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(body)
    }

    @PostMapping("/{executionId}/input")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submitInput(
        @PathVariable snippetId: UUID,
        @PathVariable executionId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: ExecutionInputRequest,
    ) {
        executionService.sendInput(snippetId, jwt.subject, executionId, request.value)
    }

    @DeleteMapping("/{executionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(
        @PathVariable snippetId: UUID,
        @PathVariable executionId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        executionService.cancel(snippetId, jwt.subject, executionId)
    }

    companion object {
        private const val STREAM_BUFFER_BYTES = 512
    }
}
