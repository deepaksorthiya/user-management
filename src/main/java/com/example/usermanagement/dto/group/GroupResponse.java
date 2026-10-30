package com.example.usermanagement.dto.group;

import com.example.usermanagement.model.UserGroup;

import java.time.LocalDateTime;

public record GroupResponse(
        Long id,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static GroupResponse from(UserGroup group) {
        return new GroupResponse(
                group.getId(), group.getName(), group.getDescription(),
                group.getCreatedAt(), group.getUpdatedAt());
    }
}
