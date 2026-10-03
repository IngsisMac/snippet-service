package ingsis.snippet.service.language

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Catálogo de lenguajes que la plataforma acepta. Hoy es configuración estática de este
 * servicio; cuando el runner modele el puerto `Language` (D9) la fuente pasará a ser él y
 * este catálogo quedará como caché o desaparecerá. La UI lo consume para armar el selector de
 * lenguaje y resolver la extensión al subir un archivo.
 */
@ConfigurationProperties(prefix = "snippet.catalog")
data class LanguageCatalogProperties(
    val languages: List<SupportedLanguage> = listOf(SupportedLanguage.PRINTSCRIPT),
)

data class SupportedLanguage(
    val language: String,
    val extension: String,
    val version: String,
) {
    companion object {
        val PRINTSCRIPT = SupportedLanguage(language = "printscript", extension = "prs", version = "1.1")
    }
}
