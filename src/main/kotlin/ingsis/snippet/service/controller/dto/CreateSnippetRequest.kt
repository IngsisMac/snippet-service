package ingsis.snippet.service.controller.dto

import ingsis.snippet.service.domain.model.Snippet
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateSnippetRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val language: String,
    @field:NotBlank
    val version: String,
    val content: String = "",
    @field:Size(max = Snippet.DESCRIPTION_MAX_LENGTH)
    val description: String? = null,
)
