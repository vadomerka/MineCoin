package com.minecoin.user.service;

public record UpdateUserCommand(String email, String displayName) {
}
