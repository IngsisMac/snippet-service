package ingsis.snippet.service.client

import ingsis.snippet.service.client.dto.TestSnippetClientResponse
import ingsis.snippet.service.client.dto.ValidateSnippetResponse

interface RunnerClient {
    fun validate(
        content: String,
        version: String,
    ): ValidateSnippetResponse

    fun runTest(
        content: String,
        version: String,
        inputs: List<String> = emptyList(),
        expectedOutputs: List<String> = emptyList(),
        env: Map<String, String>? = null,
    ): TestSnippetClientResponse
}
