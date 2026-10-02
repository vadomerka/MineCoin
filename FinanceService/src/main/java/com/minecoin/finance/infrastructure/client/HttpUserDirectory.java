package com.minecoin.finance.infrastructure.client;

import com.minecoin.finance.service.UserDirectory;
import com.minecoin.finance.service.UserDirectoryUnavailableException;
import com.minecoin.finance.service.UserRef;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpUserDirectory implements UserDirectory {

    private final RestClient restClient;

    public HttpUserDirectory(RestClient userServiceRestClient) {
        this.restClient = userServiceRestClient;
    }

    @Override
    public Optional<UserRef> findByUsername(String username) {
        RestClient.RequestHeadersSpec<?> request = restClient.get()
                .uri("/api/users/lookup?username={username}", username);
        request.header(HttpHeaders.AUTHORIZATION, bearerToken());
        try {
            return request.exchange((req, response) -> {
                if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                    return Optional.empty();
                }
                if (response.getStatusCode().isError()) {
                    throw unavailable(new IllegalStateException("Unexpected status " + response.getStatusCode()));
                }
                return Optional.ofNullable(response.bodyTo(UserRef.class));
            });
        } catch (RestClientException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public Map<UUID, String> findUsernames(Collection<UUID> userIds) {
        String query = userIds.stream().map(id -> "ids=" + id).collect(Collectors.joining("&"));
        RestClient.RequestHeadersSpec<?> request = restClient.get()
                .uri("/api/users/names?" + query);
        request.header(HttpHeaders.AUTHORIZATION, bearerToken());
        try {
            UserRef[] users = request.retrieve().body(UserRef[].class);
            if (users == null) {
                return Map.of();
            }
            return Arrays.stream(users).collect(Collectors.toMap(UserRef::id, UserRef::username));
        } catch (RestClientException ex) {
            throw unavailable(ex);
        }
    }

    private String bearerToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return "Bearer " + jwtAuthentication.getToken().getTokenValue();
        }
        throw new IllegalStateException("No authenticated user to forward the token of");
    }

    private static UserDirectoryUnavailableException unavailable(Exception cause) {
        return new UserDirectoryUnavailableException(cause);
    }
}
