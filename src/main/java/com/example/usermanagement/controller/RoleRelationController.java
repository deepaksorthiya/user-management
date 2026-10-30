package com.example.usermanagement.controller;

import com.example.usermanagement.dto.common.IdSetRequest;
import com.example.usermanagement.dto.common.PageResponse;
import com.example.usermanagement.dto.group.GroupResponse;
import com.example.usermanagement.dto.permission.PermissionResponse;
import com.example.usermanagement.dto.user.UserResponse;
import com.example.usermanagement.service.RoleRelationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Everything a role is related to: its permissions, and the users / groups it is assigned to. */
@RestController
@RequestMapping("/api/v1/roles/{roleId}")
public class RoleRelationController {

    private final RoleRelationService relationService;

    public RoleRelationController(RoleRelationService relationService) {
        this.relationService = relationService;
    }

    // ------------------------------------------------------------ permissions

    @GetMapping("/permissions")
    public List<PermissionResponse> getPermissions(@PathVariable Long roleId) {
        return relationService.getPermissions(roleId);
    }

    @PutMapping("/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addPermission(@PathVariable Long roleId, @PathVariable Long permissionId) {
        relationService.addPermission(roleId, permissionId);
    }

    @DeleteMapping("/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePermission(@PathVariable Long roleId, @PathVariable Long permissionId) {
        relationService.removePermission(roleId, permissionId);
    }

    /** Replace the complete set of permissions of the role. */
    @PutMapping("/permissions")
    public List<PermissionResponse> replacePermissions(@PathVariable Long roleId,
                                                       @Valid @RequestBody IdSetRequest request) {
        return relationService.replacePermissions(roleId, request.ids());
    }

    // ------------------------------------------------------- who has the role

    /** Users that have the role assigned directly. */
    @GetMapping("/users")
    public PageResponse<UserResponse> getUsers(
            @PathVariable Long roleId,
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        return relationService.getUsers(roleId, pageable);
    }

    /** Groups that have the role assigned. */
    @GetMapping("/groups")
    public List<GroupResponse> getGroups(@PathVariable Long roleId) {
        return relationService.getGroups(roleId);
    }
}
