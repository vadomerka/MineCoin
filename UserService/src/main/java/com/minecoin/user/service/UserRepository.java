package com.minecoin.user.service;

import com.minecoin.user.domain.User;
import com.minecoin.user.domain.UserStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Page<User> findAllByStatusNot(UserStatus status, Pageable pageable);
}
