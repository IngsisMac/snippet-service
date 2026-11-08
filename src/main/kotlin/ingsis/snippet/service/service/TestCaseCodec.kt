package ingsis.snippet.service.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.domain.model.TestCase
import org.springframework.stereotype.Component

/**
 * Inputs, outputs esperados y variables de entorno de un test case, ya deserializados.
 */
data class TestCasePayload(
    val inputs: List<String>,
    val expectedOutputs: List<String>,
    val env: Map<String, String>?,
)

/**
 * Traduce entre las columnas JSON de `test_cases` y `test_case_results` y las listas que usa el
 * resto del servicio. Un JSON ilegible se trata como vacío en lugar de romper la corrida, igual
 * que hace [ingsis.snippet.service.domain.model.LintFindingsCodec] con los hallazgos del linter.
 */
@Component
class TestCaseCodec(
    private val objectMapper: ObjectMapper = ObjectMapper(),
) {
    fun payloadOf(testCase: TestCase): TestCasePayload =
        TestCasePayload(
            inputs = decodeList(testCase.inputs),
            expectedOutputs = decodeList(testCase.expectedOutputs),
            env = decodeMap(testCase.env),
        )

    fun encode(values: List<String>): String = objectMapper.writeValueAsString(values)

    fun encodeEnv(env: Map<String, String>): String = objectMapper.writeValueAsString(env)

    @Suppress("TooGenericExceptionCaught")
    fun decodeList(json: String): List<String> =
        try {
            objectMapper.readValue(json, object : TypeReference<List<String>>() {})
        } catch (_: Exception) {
            emptyList()
        }

    @Suppress("TooGenericExceptionCaught")
    private fun decodeMap(json: String?): Map<String, String>? =
        json?.let {
            try {
                objectMapper.readValue(it, object : TypeReference<Map<String, String>>() {})
            } catch (_: Exception) {
                null
            }
        }
}
