package com.example.usermanagement.dto.role;

import com.example.usermanagement.model.Role;

import java.time.LocalDateTime;

public record RoleResponse(
        Long id,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RoleResponse from(Role role) {
        return new RoleResponse(
                role.getId(), role.getName(), role.getDescription(),
                role.getCreatedAt(), role.getUpdatedAt());
    }
}
