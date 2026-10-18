package ingsis.snippet.service.client.dto

import java.util.UUID

data class StartExecutionClientRequest(
    val content: String,
    val version: String,
    val env: Map<String, String> = emptyMap(),
)

data class StartExecutionClientResponse(
    val executionId: UUID,
)

data class ExecutionInputClientRequest(
    val value: String,
)
