package ingsis.snippet.service.controller.dto

import ingsis.snippet.service.domain.model.Snippet
import jakarta.validation.constraints.Size

data class UpdateSnippetRequest(
    val name: String? = null,
    val content: String? = null,
    @field:Size(max = Snippet.DESCRIPTION_MAX_LENGTH)
    val description: String? = null,
)
