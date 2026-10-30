package com.example.usermanagement.dto.common;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** Body for "replace all assignments" operations. An empty set removes every assignment. */
public record IdSetRequest(
        @NotNull(message = "ids is required")
        Set<@NotNull(message = "ids must not contain null") Long> ids
) {
}
