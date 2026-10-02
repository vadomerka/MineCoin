package com.minecoin.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.minecoin.finance.service.UserDirectory;
import com.minecoin.finance.service.UserDirectoryUnavailableException;
import com.minecoin.finance.service.UserRef;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=true",
                "app.finance.max-operation-amount=1000",
                "app.jwt.secret=" + WalletApiIT.SECRET,
                "app.user-service.url=http://localhost:1"
        })
class WalletApiIT {

    static final String SECRET = "test-secret-test-secret-test-secret-123";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    UserDirectory userDirectory;

    RestClient client;
    UUID alice;
    UUID bob;
    String aliceToken;
    String bobToken;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE operations, wallets");
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api/wallets")
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                })
                .build();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        aliceToken = tokenFor(alice, SECRET);
        bobToken = tokenFor(bob, SECRET);
        when(userDirectory.findByUsername("bob")).thenReturn(Optional.of(new UserRef(bob, "bob")));
        when(userDirectory.findByUsername("alice")).thenReturn(Optional.of(new UserRef(alice, "alice")));
        when(userDirectory.findByUsername("nobody")).thenReturn(Optional.empty());
    }

    @Test
    void endpointsRequireValidToken() {
        assertError(get("/me", null), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        assertError(get("/me", tokenFor(alice, "another-secret-another-secret-another-secret")),
                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    @Test
    void getMyWalletOpensEmptyWalletOnce() {
        ResponseEntity<Map<String, Object>> first = get("/me", aliceToken);
        ResponseEntity<Map<String, Object>> second = get("/me", aliceToken);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).containsEntry("balance", 0);
        assertThat(second.getBody()).containsEntry("id", first.getBody().get("id"));
    }

    @Test
    void depositAndWithdrawChangeBalance() {
        assertThat(post("/me/deposit", aliceToken, Map.of("amount", 700)).getBody()).containsEntry("balance", 700);

        ResponseEntity<Map<String, Object>> withdrawn = post("/me/withdraw", aliceToken, Map.of("amount", 200));

        assertThat(withdrawn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withdrawn.getBody()).containsEntry("balance", 500);
    }

    @Test
    void amountRulesAreEnforced() {
        post("/me/deposit", aliceToken, Map.of("amount", 100));

        assertError(post("/me/withdraw", aliceToken, Map.of("amount", 101)),
                HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_FUNDS");
        assertError(post("/me/deposit", aliceToken, Map.of("amount", 1001)),
                HttpStatus.UNPROCESSABLE_CONTENT, "AMOUNT_LIMIT_EXCEEDED");
        assertError(post("/me/deposit", aliceToken, Map.of("amount", 0)),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertError(post("/me/deposit", aliceToken, Map.of()),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat(get("/me", aliceToken).getBody()).containsEntry("balance", 100);
    }

    @Test
    void transferMovesMoneyBetweenUsers() {
        post("/me/deposit", aliceToken, Map.of("amount", 500));

        ResponseEntity<Map<String, Object>> response = post("/me/transfer", aliceToken,
                Map.of("toUsername", "bob", "amount", 200));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("balance", 300);
        assertThat(get("/me", bobToken).getBody()).containsEntry("balance", 200);
    }

    @Test
    void transferErrorsAreReported() {
        post("/me/deposit", aliceToken, Map.of("amount", 100));

        assertError(post("/me/transfer", aliceToken, Map.of("toUsername", "nobody", "amount", 10)),
                HttpStatus.NOT_FOUND, "RECIPIENT_NOT_FOUND");
        assertError(post("/me/transfer", aliceToken, Map.of("toUsername", "alice", "amount", 10)),
                HttpStatus.UNPROCESSABLE_CONTENT, "SELF_TRANSFER");
        assertError(post("/me/transfer", aliceToken, Map.of("toUsername", "bob", "amount", 101)),
                HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_FUNDS");
        assertError(post("/me/transfer", aliceToken, Map.of("toUsername", "", "amount", 10)),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

        when(userDirectory.findByUsername("bob"))
                .thenThrow(new UserDirectoryUnavailableException(new RuntimeException("down")));
        assertError(post("/me/transfer", aliceToken, Map.of("toUsername", "bob", "amount", 10)),
                HttpStatus.SERVICE_UNAVAILABLE, "USER_SERVICE_UNAVAILABLE");
        assertThat(get("/me", aliceToken).getBody()).containsEntry("balance", 100);
    }

    @Test
    void operationsShowHistoryWithDirectionAndCounterparty() {
        post("/me/deposit", aliceToken, Map.of("amount", 500));
        post("/me/transfer", aliceToken, Map.of("toUsername", "bob", "amount", 200));
        post("/me/withdraw", aliceToken, Map.of("amount", 50));
        when(userDirectory.findUsernames(anyCollection())).thenReturn(Map.of(bob, "bob"));

        ResponseEntity<Map<String, Object>> response = get("/me/operations?page=0&size=10", aliceToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("totalElements", 3);
        List<Map<String, Object>> items = itemsOf(response);
        assertThat(items).extracting(item -> item.get("type") + ":" + item.get("direction"))
                .containsExactly("WITHDRAW:OUT", "TRANSFER:OUT", "DEPOSIT:IN");
        assertThat(items.get(1))
                .containsEntry("amount", 200)
                .containsEntry("counterpartyId", bob.toString())
                .containsEntry("counterpartyUsername", "bob");
        assertError(get("/me/operations?size=500", aliceToken), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void operationsWorkWithoutUsernamesWhenUserServiceIsDown() {
        post("/me/deposit", aliceToken, Map.of("amount", 500));
        post("/me/transfer", aliceToken, Map.of("toUsername", "bob", "amount", 200));
        when(userDirectory.findUsernames(anyCollection()))
                .thenThrow(new UserDirectoryUnavailableException(new RuntimeException("down")));

        ResponseEntity<Map<String, Object>> response = get("/me/operations", bobToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(itemsOf(response).getFirst())
                .containsEntry("direction", "IN")
                .containsEntry("counterpartyId", alice.toString())
                .containsEntry("counterpartyUsername", null);
    }

    @Test
    void limitsExposeMaxOperationAmount() {
        assertThat(get("/limits", aliceToken).getBody()).containsEntry("maxOperationAmount", 1000);
    }

    private ResponseEntity<Map<String, Object>> get(String uri, String token) {
        RestClient.RequestHeadersSpec<?> request = client.get().uri(uri);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.retrieve().toEntity(JSON);
    }

    private ResponseEntity<Map<String, Object>> post(String uri, String token, Map<String, Object> body) {
        return client.post().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(JSON);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> itemsOf(ResponseEntity<Map<String, Object>> response) {
        return (List<Map<String, Object>>) response.getBody().get("items");
    }

    private void assertError(ResponseEntity<Map<String, Object>> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).containsEntry("code", code);
    }

    private static String tokenFor(UUID userId, String secret) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim("role", "USER")
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
