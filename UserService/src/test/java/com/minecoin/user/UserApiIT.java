package com.minecoin.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
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

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    RestClient client;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE users");
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api/users")
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
        assertThat(response.getHeaders().getLocation()).hasToString("/api/users/" + response.getBody().get("id"));
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
        ResponseEntity<Map<String, Object>> response = post(Map.of(
                "username", "b!", "email", "nope", "password", "123"));

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat((List<?>) response.getBody().get("errors")).hasSize(4);
    }

    @Test
    void listReturnsActiveUsersPage() {
        register("bob", "bob@x.com");
        register("alice", "alice@x.com");

        ResponseEntity<Map<String, Object>> response = client.get().uri("?page=0&size=10")
                .retrieve().toEntity(JSON);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("totalElements", 2);
        assertError(client.get().uri("?size=500").retrieve().toEntity(JSON),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void updateChangesProfileAndRejectsTakenEmail() {
        String id = idOf(register("bob", "bob@x.com"));
        register("alice", "alice@x.com");

        ResponseEntity<Map<String, Object>> updated = put(id, Map.of("email", "bob2@x.com", "displayName", "Bobby"));

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody())
                .containsEntry("email", "bob2@x.com")
                .containsEntry("displayName", "Bobby");
        assertError(put(id, Map.of("email", "ALICE@x.com")), HttpStatus.CONFLICT, "EMAIL_TAKEN");
    }

    @Test
    void deleteAnonymizesUserAndFreesUsernameAndEmail() {
        String id = idOf(register("bob", "bob@x.com"));

        ResponseEntity<Void> deleted = client.delete().uri("/{id}", id).retrieve().toBodilessEntity();

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT username, email, display_name, password_hash, status FROM users WHERE id = ?::uuid", id);
        assertThat(row)
                .containsEntry("username", "~deleted-" + id.replace("-", "").substring(0, 23))
                .containsEntry("email", id + "@deleted.invalid")
                .containsEntry("display_name", null)
                .containsEntry("password_hash", "")
                .containsEntry("status", "DELETED");
        assertError(client.get().uri("/{id}", id).retrieve().toEntity(JSON), HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        assertThat(register("bob", "bob@x.com").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<Map<String, Object>> register(String username, String email) {
        return post(Map.of("username", username, "email", email, "password", "secret123"));
    }

    private ResponseEntity<Map<String, Object>> post(Map<String, Object> body) {
        return client.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(JSON);
    }

    private ResponseEntity<Map<String, Object>> put(String id, Map<String, Object> body) {
        return client.put().uri("/{id}", id).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(JSON);
    }

    private String idOf(ResponseEntity<Map<String, Object>> response) {
        return (String) response.getBody().get("id");
    }

    private void assertError(ResponseEntity<Map<String, Object>> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).containsEntry("code", code);
    }
}
