package com.example.usermanagement.dto.permission;

import com.example.usermanagement.model.Permission;

import java.time.LocalDateTime;

public record PermissionResponse(
        Long id,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getId(), permission.getName(), permission.getDescription(),
                permission.getCreatedAt(), permission.getUpdatedAt());
    }
}
