package ingsis.snippet.service.client

import ingsis.snippet.service.client.dto.ValidateSnippetResponse

interface RunnerClient {
    fun validate(
        content: String,
        version: String,
    ): ValidateSnippetResponse
}
