package ingsis.snippet.service.client.auth

/**
 * Provee el token con el que este servicio se identifica ante los demás (machine to machine).
 * Devuelve `null` cuando la autenticación entre servicios está deshabilitada.
 */
fun interface ServiceTokenProvider {
    fun token(): String?

    companion object {
        val NONE = ServiceTokenProvider { null }
    }
}
