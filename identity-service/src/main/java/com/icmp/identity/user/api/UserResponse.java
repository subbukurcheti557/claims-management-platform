package com.icmp.identity.user.api;

import java.util.UUID;

import com.icmp.identity.user.domain.AppUser;

public record UserResponse(UUID id, String email, String fullName, String role, String market) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(),
                user.getMarket());
    }
}
