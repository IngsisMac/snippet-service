package ingsis.snippet.service.redis

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.redis.event.LintRequestEvent
import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.service.SnippetContentChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Pide el linteo de un snippet cada vez que se crea o cambia su contenido (US #15).
 *
 * Sin esto un snippet nuevo quedaba en `PENDING` hasta el próximo cambio de reglas, porque el
 * único productor de `lint-request` era [RuleChangeConsumer]. Acá se reusa la misma topología:
 * el pedido lleva las reglas y la `rulesVersion` actuales del dueño, así que el resultado entra
 * por [ComplianceResultConsumer] con el mismo descarte por versión que el relinteo masivo.
 *
 * Corre después del commit del `POST`/`PUT`, en el hilo de la request: cuesta una llamada interna
 * a `permission-service` y un `XADD`. Si cualquiera falla, el snippet queda `PENDING` y se repara
 * solo en el próximo cambio de reglas; nunca hace fallar el guardado que ya commiteó.
 */
@Component
class SnippetLintRequester(
    private val permissionClient: PermissionClient,
    private val lintRequestProducer: LintRequestProducer,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Suppress("TooGenericExceptionCaught")
    fun onSnippetContentChanged(event: SnippetContentChangedEvent) {
        try {
            val ruleSet = permissionClient.getLintRuleSet(event.ownerId)
            lintRequestProducer.emit(
                LintRequestEvent(
                    snippetId = event.snippetId.toString(),
                    content = event.content,
                    version = event.version,
                    rulesVersion = ruleSet.rulesVersion,
                    rules = objectMapper.writeValueAsString(ruleSet.rules),
                ),
            )
            logger.info("Requested lint for snippet {} with rules v{}", event.snippetId, ruleSet.rulesVersion)
        } catch (ex: Exception) {
            logger.warn(
                "Could not request lint for snippet {}; it stays PENDING until the next rule change: {}",
                event.snippetId,
                ex.message,
            )
        }
    }
}
