package com.minecoin.user.infrastructure.command;

import com.minecoin.user.domain.User;
import com.minecoin.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.create-admin.enabled", havingValue = "true")
public class CreateAdminCommand implements ApplicationRunner {

    private final UserService userService;
    private final String username;
    private final String email;
    private final String password;

    public CreateAdminCommand(
            UserService userService,
            @Value("${app.create-admin.username}") String username,
            @Value("${app.create-admin.email}") String email,
            @Value("${app.create-admin.password}") String password) {
        Assert.hasText(username, "ADMIN_USERNAME must be set");
        Assert.hasText(email, "ADMIN_EMAIL must be set");
        Assert.hasText(password, "ADMIN_PASSWORD must be set");
        this.userService = userService;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        User admin = userService.createAdmin(username, email, password);
        log.info("User '{}' ({}) has role {}", admin.getUsername(), admin.getId(), admin.getRole());
    }
}
