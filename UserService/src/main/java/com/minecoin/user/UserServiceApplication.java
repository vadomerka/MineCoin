package com.minecoin.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UserServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
