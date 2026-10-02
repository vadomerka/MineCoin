package com.minecoin.user.domain;

import com.minecoin.user.domain.exception.UserDeletedException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 64)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static User register(String username, String email, String passwordHash, String displayName) {
        User user = new User();
        user.username = username;
        user.email = email;
        user.passwordHash = passwordHash;
        user.displayName = displayName;
        user.role = Role.USER;
        user.status = UserStatus.ACTIVE;
        return user;
    }

    public void updateProfile(String email, String displayName) {
        if (isDeleted()) {
            throw new UserDeletedException(id);
        }
        this.email = email;
        this.displayName = displayName;
    }

    public void delete() {
        if (isDeleted()) {
            return;
        }
        String compactId = id.toString().replace("-", "");
        username = "~deleted-" + compactId.substring(0, 23);
        email = id + "@deleted.invalid";
        displayName = null;
        passwordHash = "";
        status = UserStatus.DELETED;
    }

    public boolean isDeleted() {
        return status == UserStatus.DELETED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
