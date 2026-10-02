package com.minecoin.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecoin.user.service.UserService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=true",
                "app.jwt.secret=test-secret-test-secret-test-secret-123"
        })
class UserApiIT {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };
    private static final String PASSWORD = "secret123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserService userService;

    RestClient client;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE users");
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api")
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                })
                .build();
    }

    @Test
    void registerReturnsCreatedUserWithLocation() {
        ResponseEntity<Map<String, Object>> response = register("bob", "bob@x.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody())
                .containsEntry("username", "bob")
                .containsEntry("role", "USER")
                .containsEntry("status", "ACTIVE")
                .doesNotContainKey("passwordHash");
        assertThat(response.getHeaders().getLocation()).hasToString("/api/users/" + idOf(response));
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'bob'", String.class);
        assertThat(hash).startsWith("$2a$");
    }

    @Test
    void registerRejectsTakenUsernameAndEmailIgnoringCase() {
        register("bob", "bob@x.com");

        assertError(register("BOB", "other@x.com"), HttpStatus.CONFLICT, "USERNAME_TAKEN");
        assertError(register("alice", "Bob@X.com"), HttpStatus.CONFLICT, "EMAIL_TAKEN");
    }

    @Test
    void registerValidatesRequest() {
        ResponseEntity<Map<String, Object>> response = post("/auth/register", Map.of(
                "username", "b!", "email", "nope", "password", "123"));

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat((List<?>) response.getBody().get("errors")).hasSize(4);
    }

    @Test
    void loginReturnsBearerTokenIgnoringUsernameCase() {
        String id = idOf(register("bob", "bob@x.com"));

        ResponseEntity<Map<String, Object>> response = login("BOB", PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("tokenType", "Bearer")
                .containsKeys("accessToken", "expiresAt");
        String payload = payloadOf((String) response.getBody().get("accessToken"));
        assertThat(payload).contains("\"sub\":\"" + id + "\"").contains("\"role\":\"USER\"");
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownUserWithSameError() {
        register("bob", "bob@x.com");

        assertError(login("bob", "wrong-password"), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        assertError(login("nobody", PASSWORD), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        assertError(post("/auth/login", Map.of("username", "", "password", "")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void loginRejectsBlockedUserOnlyAfterPasswordCheck() {
        register("bob", "bob@x.com");
        jdbc.update("UPDATE users SET status = 'BLOCKED' WHERE username = 'bob'");

        assertError(login("bob", PASSWORD), HttpStatus.FORBIDDEN, "USER_BLOCKED");
        assertError(login("bob", "wrong-password"), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    }

    @Test
    void protectedEndpointsRejectMissingOrTamperedToken() {
        register("bob", "bob@x.com");
        String token = tokenOf("bob");
        String[] parts = token.split("\\.");
        String forgedPayload = encode(payloadOf(token).replace("\"USER\"", "\"ADMIN\""));
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        assertError(get("/users/me", null), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        assertError(get("/users/me", "not-a-token"), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        assertError(get("/users", tampered), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        assertThat(get("/users/me", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void meEndpointsReadUpdateAndDeleteCurrentUser() {
        String id = idOf(register("bob", "bob@x.com"));
        register("alice", "alice@x.com");
        String token = tokenOf("bob");

        assertThat(get("/users/me", token).getBody()).containsEntry("id", id);
        ResponseEntity<Map<String, Object>> updated = put("/users/me", token,
                Map.of("email", "bob2@x.com", "displayName", "Bobby"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody())
                .containsEntry("email", "bob2@x.com")
                .containsEntry("displayName", "Bobby");
        assertError(put("/users/me", token, Map.of("email", "ALICE@x.com")), HttpStatus.CONFLICT, "EMAIL_TAKEN");

        assertThat(delete("/users/me", token).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT username, email, display_name, password_hash, status FROM users WHERE id = ?::uuid", id);
        assertThat(row)
                .containsEntry("username", "~deleted-" + id.replace("-", "").substring(0, 23))
                .containsEntry("email", id + "@deleted.invalid")
                .containsEntry("display_name", null)
                .containsEntry("password_hash", "")
                .containsEntry("status", "DELETED");
        assertError(get("/users/me", token), HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        assertError(login("bob", PASSWORD), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        assertThat(register("bob", "bob2@x.com").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void adminEndpointsRequireAdminRole() {
        register("admin", "admin@x.com");
        String bobId = idOf(register("bob", "bob@x.com"));
        String userToken = tokenOf("admin");
        assertError(get("/users", userToken), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertError(get("/users/" + bobId, userToken), HttpStatus.FORBIDDEN, "FORBIDDEN");

        userService.createAdmin("admin", "admin@x.com", PASSWORD);
        String adminToken = tokenOf("admin");

        ResponseEntity<Map<String, Object>> list = get("/users?page=0&size=10", adminToken);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).containsEntry("totalElements", 2);
        assertError(get("/users?size=500", adminToken), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat(get("/users/" + bobId, adminToken).getBody()).containsEntry("username", "bob");
        assertThat(put("/users/" + bobId, adminToken, Map.of("email", "bob2@x.com")).getBody())
                .containsEntry("email", "bob2@x.com");
        assertThat(delete("/users/" + bobId, adminToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertError(get("/users/" + bobId, adminToken), HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    @Test
    void createAdminRegistersNewAdminWhoCanLogIn() {
        userService.createAdmin("root", "root@x.com", PASSWORD);

        String token = tokenOf("root");

        assertThat(payloadOf(token)).contains("\"role\":\"ADMIN\"");
        assertThat(get("/users", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map<String, Object>> register(String username, String email) {
        return post("/auth/register", Map.of("username", username, "email", email, "password", PASSWORD));
    }

    private ResponseEntity<Map<String, Object>> login(String username, String password) {
        return post("/auth/login", Map.of("username", username, "password", password));
    }

    private String tokenOf(String username) {
        return (String) login(username, PASSWORD).getBody().get("accessToken");
    }

    private ResponseEntity<Map<String, Object>> post(String uri, Map<String, Object> body) {
        return client.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(JSON);
    }

    private ResponseEntity<Map<String, Object>> get(String uri, String token) {
        RestClient.RequestHeadersSpec<?> request = client.get().uri(uri);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.retrieve().toEntity(JSON);
    }

    private ResponseEntity<Map<String, Object>> put(String uri, String token, Map<String, Object> body) {
        return client.put().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(JSON);
    }

    private ResponseEntity<Void> delete(String uri, String token) {
        RestClient.RequestHeadersSpec<?> request = client.delete().uri(uri);
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request.retrieve().toBodilessEntity();
    }

    private String idOf(ResponseEntity<Map<String, Object>> response) {
        return (String) response.getBody().get("id");
    }

    private String payloadOf(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    private String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertError(ResponseEntity<Map<String, Object>> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).containsEntry("code", code);
    }
}
