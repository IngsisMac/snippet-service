package ingsis.snippet.service.domain.model

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.client.dto.LintFindingClientDto
import org.springframework.stereotype.Component

/**
 * `snippet_statuses.findings` guarda los hallazgos del último linteo como JSON para que el detalle
 * del snippet los muestre sin volver a llamar al runner. Un JSON ilegible (por ejemplo, escrito por
 * una versión anterior del evento) se trata como "sin hallazgos" en vez de romper la lectura.
 */
@Component
class LintFindingsCodec(
    private val objectMapper: ObjectMapper = ObjectMapper(),
) {
    fun encode(findings: List<LintFindingClientDto>): String = objectMapper.writeValueAsString(findings)

    @Suppress("TooGenericExceptionCaught")
    fun decode(json: String): List<LintFindingClientDto> =
        try {
            objectMapper.readValue(json, object : TypeReference<List<LintFindingClientDto>>() {})
        } catch (_: Exception) {
            emptyList()
        }

    companion object {
        const val EMPTY = "[]"
    }
}
