package ingsis.snippet.service.client

import ingsis.snippet.service.client.dto.PermissionLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.util.UUID

class HttpPermissionClientTest {
    private lateinit var restClient: RestClient
    private lateinit var mockServer: MockRestServiceServer
    private lateinit var permissionClient: HttpPermissionClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl("http://permission-service:8081")
        mockServer = MockRestServiceServer.bindTo(builder).build()
        restClient = builder.build()
        permissionClient = HttpPermissionClient(restClient)
    }

    @Test
    fun shouldAssignOwnerSuccessfully() {
        val snippetId = UUID.randomUUID()
        val userId = "auth0|user1"

        mockServer
            .expect(
                requestTo(
                    "http://permission-service:8081/internal/permissions?snippetId=$snippetId&userId=auth0%7Cuser1"
                ),
            ).andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess())

        permissionClient.assignOwner(snippetId, userId)

        mockServer.verify()
    }

    @Test
    fun shouldGetPermissionLevelSuccessfully() {
        val snippetId = UUID.randomUUID()
        val userId = "auth0|user1"

        mockServer
            .expect(requestTo("http://permission-service:8081/internal/permissions/$snippetId?user=auth0%7Cuser1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"level":"OWNER"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val level = permissionClient.getPermission(snippetId, userId)

        assertEquals(PermissionLevel.OWNER, level)
        mockServer.verify()
    }

    @Test
    fun shouldReturnNoneWhenPermissionLevelIsUnknownOrEmpty() {
        val snippetId = UUID.randomUUID()
        val userId = "auth0|user2"

        mockServer
            .expect(requestTo("http://permission-service:8081/internal/permissions/$snippetId?user=auth0%7Cuser2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"level":"NONE"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val level = permissionClient.getPermission(snippetId, userId)

        assertEquals(PermissionLevel.NONE, level)
        mockServer.verify()
    }

    @Test
    fun shouldGetLintRulesSuccessfully() {
        val userId = "auth0|user1"
        mockServer
            .expect(requestTo("http://permission-service:8081/internal/rules/lint/auth0%7Cuser1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"userId":"auth0|user1","rulesVersion":1,"rules":{"printscript.no-println":true}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val rules = permissionClient.getLintRules(userId)

        assertEquals(true, rules["printscript.no-println"])
        mockServer.verify()
    }

    @Test
    fun shouldGetFormatRulesSuccessfully() {
        val userId = "auth0|user1"
        mockServer
            .expect(requestTo("http://permission-service:8081/internal/rules/format/auth0%7Cuser1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"userId":"auth0|user1","rulesVersion":1,"rules":{"printscript.space-before-colon":true}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val rules = permissionClient.getFormatRules(userId)

        assertEquals(true, rules["printscript.space-before-colon"])
        mockServer.verify()
    }

    @Test
    fun shouldGetUserPermissionsSuccessfully() {
        val userId = "auth0|user1"
        val snippetId = UUID.randomUUID()
        mockServer
            .expect(requestTo("http://permission-service:8081/internal/permissions/user/auth0%7Cuser1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """[{"snippetId":"$snippetId","userId":"auth0|user1","level":"READ","grantedAt":"2026-09-30T10:00:00Z"}]""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val permissions = permissionClient.getUserPermissions(userId)

        assertEquals(1, permissions.size)
        assertEquals(snippetId, permissions[0].snippetId)
        assertEquals(PermissionLevel.READ, permissions[0].level)
        mockServer.verify()
    }

    @Test
    fun shouldGetUserPermissionsFilteredByLevelSuccessfully() {
        val userId = "auth0|user1"
        val snippetId = UUID.randomUUID()
        mockServer
            .expect(requestTo("http://permission-service:8081/internal/permissions/user/auth0%7Cuser1?level=WRITE"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """[{"snippetId":"$snippetId","userId":"auth0|user1","level":"WRITE","grantedAt":"2026-09-30T10:00:00Z"}]""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val permissions = permissionClient.getUserPermissions(userId, PermissionLevel.WRITE)

        assertEquals(1, permissions.size)
        assertEquals(snippetId, permissions[0].snippetId)
        assertEquals(PermissionLevel.WRITE, permissions[0].level)
        mockServer.verify()
    }
}
