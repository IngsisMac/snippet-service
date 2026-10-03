package ingsis.snippet.service.controller.dto

/** Body opcional de `POST /api/snippets/{id}/format`: texto a formatear en lugar del guardado. */
data class FormatSnippetRequest(
    val content: String? = null,
)
