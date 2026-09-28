package ingsis.snippet.service.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "test_cases")
class TestCase(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "snippet_id", nullable = false)
    val snippetId: UUID,
    @Column(nullable = false)
    var name: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    var inputs: String = "[]",
    @Column(name = "expected_outputs", nullable = false, columnDefinition = "TEXT")
    var expectedOutputs: String = "[]",
    @Column(columnDefinition = "TEXT")
    var env: String? = null,
)
