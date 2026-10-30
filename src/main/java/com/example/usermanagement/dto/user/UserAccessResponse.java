package com.example.usermanagement.dto.user;

import java.util.List;

/**
 * What a user can actually do: direct roles + roles inherited from groups,
 * flattened into the final set of permissions.
 */
public record UserAccessResponse(
        Long userId,
        String username,
        Boolean active,
        List<String> groups,
        List<String> directRoles,
        List<String> groupRoles,
        List<String> permissions
) {
}
