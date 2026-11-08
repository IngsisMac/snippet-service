package ingsis.snippet.service.service

import java.util.UUID

/**
 * El contenido de un snippet cambió: se creó o se guardó una versión nueva.
 *
 * Lo publica [SnippetService] dentro de la transacción del `POST` o del `PUT`, pero los que lo
 * consumen esperan a que esa transacción commitee: si el guardado terminara descartándose, no
 * queremos haber corrido tests ni pedido un linteo de un contenido que nunca existió. El contenido
 * viaja en el evento para que cada consumidor use exactamente el texto que se guardó, y no lo que
 * haya en el store cuando el hilo de fondo llegue a leerlo.
 *
 * Reaccionan dos listeners: [SnippetTestRunner] (US #16, corre los test cases) y
 * [ingsis.snippet.service.redis.SnippetLintRequester] (US #15, pide el linteo con las reglas del
 * dueño). `ownerId` es el registrado en `snippets.owner_id`, que es quien creó el snippet.
 */
data class SnippetContentChangedEvent(
    val snippetId: UUID,
    val content: String,
    val version: String,
    val ownerId: String,
)
