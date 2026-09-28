package ingsis.snippet.service.controller.dto

import jakarta.validation.constraints.NotBlank

data class CreateSnippetRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val language: String,
    @field:NotBlank
    val version: String,
    val content: String = "",
)
