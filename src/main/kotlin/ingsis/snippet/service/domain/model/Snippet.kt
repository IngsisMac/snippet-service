package ingsis.snippet.service.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Metadata del snippet. El contenido no vive acá: está en el asset-service y se accede por
 * `SnippetStore` usando el `id`. `ownerId` registra quién lo creó; los permisos efectivos
 * (incluida una transferencia de ownership) los decide `permission-service`.
 */
@Entity
@Table(name = "snippets")
class Snippet(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var name: String,
    @Column(name = "owner_id", nullable = false)
    val ownerId: String,
    @Column(nullable = false)
    var language: String,
    @Column(nullable = false)
    var version: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
