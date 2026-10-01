package com.minecoin.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minecoin.user.domain.exception.UserDeletedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTest {

    private static final UUID ID = UUID.fromString("b0434c38-cdd2-4099-a9a0-cf0efbfda999");

    @Test
    void registerSetsDefaultRoleAndStatus() {
        User user = User.register("bob", "bob@x.com", "hash", "Bob");

        assertThat(user.getUsername()).isEqualTo("bob");
        assertThat(user.getEmail()).isEqualTo("bob@x.com");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getDisplayName()).isEqualTo("Bob");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void updateProfileChangesEmailAndDisplayName() {
        User user = User.register("bob", "bob@x.com", "hash", "Bob");

        user.updateProfile("new@x.com", "Bobby");

        assertThat(user.getEmail()).isEqualTo("new@x.com");
        assertThat(user.getDisplayName()).isEqualTo("Bobby");
    }

    @Test
    void deleteAnonymizesPersonalData() {
        User user = registeredUserWithId();

        user.delete();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getUsername()).isEqualTo("~deleted-b0434c38cdd24099a9a0cf0").hasSize(32);
        assertThat(user.getEmail()).isEqualTo(ID + "@deleted.invalid");
        assertThat(user.getDisplayName()).isNull();
        assertThat(user.getPasswordHash()).isEmpty();
    }

    @Test
    void deleteIsIdempotent() {
        User user = registeredUserWithId();
        user.delete();
        String username = user.getUsername();
        String email = user.getEmail();

        user.delete();

        assertThat(user.getUsername()).isEqualTo(username);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
    }

    @Test
    void updateProfileOfDeletedUserThrows() {
        User user = registeredUserWithId();
        user.delete();

        assertThatThrownBy(() -> user.updateProfile("new@x.com", "Bobby"))
                .isInstanceOf(UserDeletedException.class);
    }

    private User registeredUserWithId() {
        User user = User.register("bob", "bob@x.com", "hash", "Bob");
        ReflectionTestUtils.setField(user, "id", ID);
        return user;
    }
}
