package ingsis.snippet.service.domain.repository

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.JpaSort
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Traduce el ordenamiento público del listado a propiedades de la JPQL de
 * [SnippetRepository.findAllByIdsAndFilters].
 *
 * El estado de cumplimiento y la cantidad de hallazgos viven en `SnippetStatus`, no en `Snippet`:
 * Spring prefijaría la propiedad con el alias de la raíz y la query rompería. Se traducen al alias
 * del `LEFT JOIN` con [JpaSort.unsafe], y cualquier propiedad fuera del mapa se rechaza con 400 en
 * lugar de propagarse como un 500 de Hibernate.
 */
object SnippetListingSort {
    private const val STATUS_ALIAS = "st."

    /** Nombre público de ordenamiento -> propiedad JPQL. La interfaz llama `compliance` al estado. */
    private val SORTABLE_PROPERTIES =
        mapOf(
            "name" to "name",
            "language" to "language",
            "version" to "version",
            "createdAt" to "createdAt",
            "updatedAt" to "updatedAt",
            "status" to "${STATUS_ALIAS}status",
            "compliance" to "${STATUS_ALIAS}status",
            "findingsCount" to "${STATUS_ALIAS}findingsCount",
        )

    fun resolve(pageable: Pageable): Pageable {
        if (pageable.sort.isUnsorted) return pageable
        val resolved =
            pageable.sort.fold(Sort.unsorted()) { accumulated, order ->
                accumulated.and(orderFor(order))
            }
        return PageRequest.of(pageable.pageNumber, pageable.pageSize, resolved)
    }

    private fun orderFor(order: Sort.Order): Sort {
        val property =
            SORTABLE_PROPERTIES[order.property]
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot sort snippets by '${order.property}'",
                )
        return if (property.startsWith(STATUS_ALIAS)) {
            JpaSort.unsafe(order.direction, property)
        } else {
            Sort.by(order.direction, property)
        }
    }
}
