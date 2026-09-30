package ingsis.snippet.service.controller

import ingsis.snippet.service.controller.dto.CreateSnippetRequest
import ingsis.snippet.service.controller.dto.SnippetResponse
import ingsis.snippet.service.controller.dto.UpdateSnippetRequest
import ingsis.snippet.service.service.SnippetService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/snippets")
class SnippetController(
    private val snippetService: SnippetService,
) {
    @PostMapping
    fun createSnippet(
        @AuthenticationPrincipal jwt: Jwt?,
        @Valid @RequestBody request: CreateSnippetRequest,
    ): ResponseEntity<SnippetResponse> {
        val ownerId = jwt?.subject ?: "anonymous"
        val created = snippetService.createSnippet(ownerId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping("/{id}")
    fun getSnippet(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): SnippetResponse {
        val userId = jwt?.subject ?: "anonymous"
        return snippetService.getSnippet(id, userId)
    }

    @GetMapping
    fun listSnippets(
        @AuthenticationPrincipal jwt: Jwt?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): Page<SnippetResponse> {
        val ownerId = jwt?.subject ?: "anonymous"
        return snippetService.listSnippets(ownerId, pageable)
    }

    @PutMapping("/{id}")
    fun updateSnippet(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
        @RequestBody request: UpdateSnippetRequest,
    ): SnippetResponse {
        val ownerId = jwt?.subject ?: "anonymous"
        return snippetService.updateSnippet(id, ownerId, request)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSnippet(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ) {
        val ownerId = jwt?.subject ?: "anonymous"
        snippetService.deleteSnippet(id, ownerId)
    }
}
