package ingsis.snippet.service.controller

import ingsis.snippet.service.controller.dto.FileTypeResponse
import ingsis.snippet.service.language.LanguageCatalogProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Lenguajes y extensiones soportados. Vive bajo `/api/snippets` para entrar por la misma
 * ruta de NGINX que el resto del servicio; la ruta literal gana sobre `/{id}`.
 */
@RestController
@RequestMapping("/api/snippets/file-types")
@EnableConfigurationProperties(LanguageCatalogProperties::class)
class FileTypeController(
    private val catalog: LanguageCatalogProperties,
) {
    @GetMapping
    fun listFileTypes(): List<FileTypeResponse> =
        catalog.languages.map {
            FileTypeResponse(language = it.language, extension = it.extension, version = it.version)
        }
}
