package com.example.usermanagement.controller;

import com.example.usermanagement.dto.common.IdSetRequest;
import com.example.usermanagement.dto.group.GroupResponse;
import com.example.usermanagement.dto.role.RoleResponse;
import com.example.usermanagement.dto.user.UserAccessResponse;
import com.example.usermanagement.service.UserRelationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Everything a user is related to: direct roles, groups and the resulting access.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}")
public class UserRelationController {

    private final UserRelationService relationService;

    public UserRelationController(UserRelationService relationService) {
        this.relationService = relationService;
    }

    // ------------------------------------------------------------------ roles

    @GetMapping("/roles")
    public List<RoleResponse> getRoles(@PathVariable Long userId) {
        return relationService.getRoles(userId);
    }

    @PutMapping("/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignRole(@PathVariable Long userId, @PathVariable Long roleId) {
        relationService.assignRole(userId, roleId);
    }

    @DeleteMapping("/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRole(@PathVariable Long userId, @PathVariable Long roleId) {
        relationService.removeRole(userId, roleId);
    }

    /**
     * Replace the complete set of direct roles.
     */
    @PutMapping("/roles")
    public List<RoleResponse> replaceRoles(@PathVariable Long userId, @Valid @RequestBody IdSetRequest request) {
        return relationService.replaceRoles(userId, request.ids());
    }

    // ----------------------------------------------------------------- groups

    @GetMapping("/groups")
    public List<GroupResponse> getGroups(@PathVariable Long userId) {
        return relationService.getGroups(userId);
    }

    @PutMapping("/groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void joinGroup(@PathVariable Long userId, @PathVariable Long groupId) {
        relationService.joinGroup(userId, groupId);
    }

    @DeleteMapping("/groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveGroup(@PathVariable Long userId, @PathVariable Long groupId) {
        relationService.leaveGroup(userId, groupId);
    }

    /**
     * Replace the complete set of group memberships.
     */
    @PutMapping("/groups")
    public List<GroupResponse> replaceGroups(@PathVariable Long userId, @Valid @RequestBody IdSetRequest request) {
        return relationService.replaceGroups(userId, request.ids());
    }

    // ----------------------------------------------------------------- access

    /**
     * Effective access: direct roles + group roles flattened into permissions.
     */
    @GetMapping("/access")
    public UserAccessResponse getAccess(@PathVariable Long userId) {
        return relationService.getAccess(userId);
    }
}
