package ingsis.snippet.service.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Endpoint de prueba para simular condiciones de error HTTP 500 y validar
 * el disparo de alertas de New Relic durante la defensa y tests de observabilidad.
 */
@RestController
@RequestMapping("/api/snippets/simulate-error")
class ChaosSimulationController {
    private val logger = LoggerFactory.getLogger(ChaosSimulationController::class.java)

    @GetMapping
    fun triggerSimulatedError(
        @RequestParam(defaultValue = "Simulated 500 Error for New Relic Alert Demo") message: String,
    ): ResponseEntity<Map<String, Any>> {
        logger.error("Disparando error 500 simulado para New Relic Alert Demo: {}", message)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                mapOf(
                    "status" to HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "error" to "Internal Server Error",
                    "message" to message,
                    "simulated" to true,
                ),
            )
    }
}
