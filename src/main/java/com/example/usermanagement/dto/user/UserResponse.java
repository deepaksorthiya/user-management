package com.example.usermanagement.dto.user;

import com.example.usermanagement.model.User;

import java.time.LocalDateTime;

/** Never exposes the password hash. */
public record UserResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getActive(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
