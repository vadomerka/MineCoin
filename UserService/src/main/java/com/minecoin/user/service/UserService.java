package com.minecoin.user.service;

import com.minecoin.user.domain.User;
import com.minecoin.user.domain.UserStatus;
import com.minecoin.user.domain.exception.EmailTakenException;
import com.minecoin.user.domain.exception.InvalidCredentialsException;
import com.minecoin.user.domain.exception.UserBlockedException;
import com.minecoin.user.domain.exception.UserNotFoundException;
import com.minecoin.user.domain.exception.UsernameTakenException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(String username, String email, String password, String displayName) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new UsernameTakenException(username);
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailTakenException(email);
        }
        User user = User.register(username, email, passwordEncoder.encode(password), displayName);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User authenticate(String username, String password) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .filter(candidate -> !candidate.isDeleted())
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        if (user.isBlocked()) {
            throw new UserBlockedException();
        }
        return user;
    }

    @Transactional
    public User createAdmin(String username, String email, String password) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseGet(() -> register(username, email, password, null));
        user.promoteToAdmin();
        return user;
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return findActive(id);
    }

    @Transactional(readOnly = true)
    public Page<User> list(Pageable pageable) {
        return userRepository.findAllByStatusNot(UserStatus.DELETED, pageable);
    }

    @Transactional
    public User update(UUID id, UpdateUserCommand command) {
        User user = findActive(id);
        boolean emailChanged = !user.getEmail().equalsIgnoreCase(command.email());
        if (emailChanged && userRepository.existsByEmailIgnoreCase(command.email())) {
            throw new EmailTakenException(command.email());
        }
        user.updateProfile(command.email(), command.displayName());
        return user;
    }

    @Transactional
    public void delete(UUID id) {
        findActive(id).delete();
    }

    private User findActive(UUID id) {
        return userRepository.findById(id)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
