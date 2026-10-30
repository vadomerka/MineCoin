package com.minecoin.prober;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class HttpMineCoinApi implements MineCoinApi {

    private static final Set<Integer> OK = Set.of(200);
    private static final Set<Integer> CREATED_OR_EXISTS = Set.of(201, 409);

    private final HttpClient client;
    private final JsonMapper json = JsonMapper.builder().build();
    private final URI baseUrl;
    private final Duration timeout;

    public HttpMineCoinApi(URI baseUrl, Duration timeout) {
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.baseUrl = baseUrl;
        this.timeout = timeout;
    }

    @Override
    public void home() {
        send(request("/").GET(), OK);
    }

    @Override
    public void register(String username, String password) {
        send(post("/api/auth/register", null, Map.of(
                "username", username,
                "email", username + "@prober.minecoin.local",
                "password", password)), CREATED_OR_EXISTS);
    }

    @Override
    public Session login(String username, String password) {
        String body = send(post("/api/auth/login", null, Map.of("username", username, "password", password)), OK);
        JsonNode token = json.readTree(body);
        return new Session(token.path("accessToken").asString(), Instant.parse(token.path("expiresAt").asString()));
    }

    @Override
    public void wallet(String token) {
        send(request("/api/wallets/me").header("Authorization", "Bearer " + token).GET(), OK);
    }

    @Override
    public void deposit(String token, long amount) {
        send(post("/api/wallets/me/deposit", token, Map.of("amount", amount)), OK);
    }

    @Override
    public void transfer(String token, String toUsername, long amount) {
        send(post("/api/wallets/me/transfer", token, Map.of("toUsername", toUsername, "amount", amount)), OK);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(baseUrl.resolve(path)).timeout(timeout);
    }

    private HttpRequest.Builder post(String path, String token, Map<String, ?> body) {
        HttpRequest.Builder builder = request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private String send(HttpRequest.Builder builder, Set<Integer> expected) {
        HttpRequest request = builder.header("User-Agent", "minecoin-prober").build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (!expected.contains(response.statusCode())) {
                throw new ProbeFailedException(
                        request.method() + " " + request.uri().getPath() + " returned " + response.statusCode(),
                        response.statusCode());
            }
            return response.body();
        } catch (IOException ex) {
            throw new ProbeFailedException(request.method() + " " + request.uri().getPath() + " failed: " + ex, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProbeFailedException(request.method() + " " + request.uri().getPath() + " interrupted", ex);
        }
    }
}
