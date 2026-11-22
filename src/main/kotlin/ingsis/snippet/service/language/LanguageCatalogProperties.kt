package ingsis.snippet.service.language

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Catálogo de lenguajes que la plataforma acepta. Hoy es configuración estática de este
 * servicio; cuando el runner modele el puerto `Language` (D9) la fuente pasará a ser él y
 * este catálogo quedará como caché o desaparecerá. La UI lo consume para armar el selector de
 * lenguaje y resolver la extensión al subir un archivo.
 *
 * Cada entrada es un par lenguaje + versión: un lenguaje con varias versiones aparece una vez por
 * versión, en orden ascendente (la última es la que la UI propone por defecto). PrintScript 1.1 es
 * una extensión de la 1.0, y ambas versiones las acepta el runner.
 */
@ConfigurationProperties(prefix = "snippet.catalog")
data class LanguageCatalogProperties(
    val languages: List<SupportedLanguage> =
        listOf(SupportedLanguage.PRINTSCRIPT_1_0, SupportedLanguage.PRINTSCRIPT_1_1),
)

data class SupportedLanguage(
    val language: String,
    val extension: String,
    val version: String,
) {
    companion object {
        val PRINTSCRIPT_1_0 = SupportedLanguage(language = "printscript", extension = "prs", version = "1.0")
        val PRINTSCRIPT_1_1 = SupportedLanguage(language = "printscript", extension = "prs", version = "1.1")
    }
}
