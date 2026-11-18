package com.example.usermanagement.controller;

import com.example.usermanagement.dto.common.PageResponse;
import com.example.usermanagement.dto.group.GroupResponse;
import com.example.usermanagement.dto.role.RoleResponse;
import com.example.usermanagement.dto.user.UserResponse;
import com.example.usermanagement.service.PermissionRelationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of a permission's relations. The role -> permission link is owned by the role
 * ({@link RoleRelationController}) and the group -> permission link by the group
 * ({@link GroupRelationController}), so grant / revoke lives there.
 */
@RestController
@RequestMapping("/api/v1/permissions/{permissionId}")
public class PermissionRelationController {

    private final PermissionRelationService relationService;

    public PermissionRelationController(PermissionRelationService relationService) {
        this.relationService = relationService;
    }

    /**
     * Roles that grant this permission.
     */
    @GetMapping("/roles")
    public List<RoleResponse> getRoles(@PathVariable Long permissionId) {
        return relationService.getRoles(permissionId);
    }

    /**
     * Users that have this permission granted directly (paged).
     */
    @GetMapping("/users")
    public PageResponse<UserResponse> getUsers(
            @PathVariable Long permissionId,
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        return relationService.getUsers(permissionId, pageable);
    }

    /**
     * Groups that have this permission granted directly.
     */
    @GetMapping("/groups")
    public List<GroupResponse> getGroups(@PathVariable Long permissionId) {
        return relationService.getGroups(permissionId);
    }
}
