package com.example.usermanagement.dto.permission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PermissionRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Name must be UPPER_SNAKE_CASE")
        String name,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description
) {
}
