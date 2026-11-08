package ingsis.snippet.service.service

import ingsis.snippet.service.client.PermissionClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class SnippetPermissionCleanerTest {
    private lateinit var permissionClient: PermissionClient
    private lateinit var cleaner: SnippetPermissionCleaner

    @BeforeEach
    fun setUp() {
        permissionClient = mock()
        cleaner = SnippetPermissionCleaner(permissionClient)
    }

    @Test
    fun shouldDeletePermissionsWhenSnippetIsDeleted() {
        val snippetId = UUID.randomUUID()

        cleaner.onSnippetDeleted(SnippetDeletedEvent(snippetId))

        verify(permissionClient).deletePermissions(snippetId)
    }

    @Test
    fun shouldNotThrowWhenPermissionClientFails() {
        val snippetId = UUID.randomUUID()
        whenever(permissionClient.deletePermissions(snippetId)).thenThrow(RuntimeException("Network error"))

        // No debe lanzar excepción para no fallar la transacción que ya commiteó
        cleaner.onSnippetDeleted(SnippetDeletedEvent(snippetId))

        verify(permissionClient).deletePermissions(snippetId)
    }
}
