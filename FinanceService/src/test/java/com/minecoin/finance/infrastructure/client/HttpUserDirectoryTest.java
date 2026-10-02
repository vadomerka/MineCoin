package com.minecoin.finance.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.minecoin.finance.service.UserDirectoryUnavailableException;
import com.minecoin.finance.service.UserRef;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpUserDirectoryTest {

    private static final UUID BOB = UUID.fromString("b0434c38-cdd2-4099-a9a0-cf0efbfda999");
    private static final UUID ALICE = UUID.fromString("6ed592fb-07e6-4449-b449-f91069f16abf");

    private MockRestServiceServer server;
    private HttpUserDirectory directory;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://user-service");
        server = MockRestServiceServer.bindTo(builder).build();
        directory = new HttpUserDirectory(builder.build());
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject(ALICE.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findByUsernameReturnsUserAndForwardsToken() {
        server.expect(requestTo("http://user-service/api/users/lookup?username=bob"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess("{\"id\":\"" + BOB + "\",\"username\":\"bob\"}", MediaType.APPLICATION_JSON));

        assertThat(directory.findByUsername("bob")).contains(new UserRef(BOB, "bob"));
        server.verify();
    }

    @Test
    void findByUsernameReturnsEmptyWhenUserServiceAnswersNotFound() {
        server.expect(requestTo("http://user-service/api/users/lookup?username=nobody"))
                .andRespond(withResourceNotFound());

        assertThat(directory.findByUsername("nobody")).isEmpty();
    }

    @Test
    void findByUsernameFailsAsUnavailableOnServerError() {
        server.expect(requestTo("http://user-service/api/users/lookup?username=bob"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> directory.findByUsername("bob"))
                .isInstanceOf(UserDirectoryUnavailableException.class);
    }

    @Test
    void findByUsernameFailsAsUnavailableWhenConnectionFails() {
        server.expect(requestTo("http://user-service/api/users/lookup?username=bob"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> directory.findByUsername("bob"))
                .isInstanceOf(UserDirectoryUnavailableException.class);
    }

    @Test
    void findUsernamesReturnsMapByUserId() {
        server.expect(requestTo("http://user-service/api/users/names?ids=" + BOB + "&ids=" + ALICE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess(
                        "[{\"id\":\"" + BOB + "\",\"username\":\"bob\"},{\"id\":\"" + ALICE + "\",\"username\":\"alice\"}]",
                        MediaType.APPLICATION_JSON));

        assertThat(directory.findUsernames(List.of(BOB, ALICE))).isEqualTo(Map.of(BOB, "bob", ALICE, "alice"));
    }

    @Test
    void findUsernamesFailsAsUnavailableOnServerError() {
        server.expect(requestTo("http://user-service/api/users/names?ids=" + BOB))
                .andRespond(withServerError());

        assertThatThrownBy(() -> directory.findUsernames(List.of(BOB)))
                .isInstanceOf(UserDirectoryUnavailableException.class);
    }
}
