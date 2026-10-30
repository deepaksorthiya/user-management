package com.example.usermanagement.controller;

import com.example.usermanagement.dto.common.IdSetRequest;
import com.example.usermanagement.dto.common.PageResponse;
import com.example.usermanagement.dto.role.RoleResponse;
import com.example.usermanagement.dto.user.UserResponse;
import com.example.usermanagement.service.GroupRelationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Everything a group is related to: its members and its roles.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}")
public class GroupRelationController {

    private final GroupRelationService relationService;

    public GroupRelationController(GroupRelationService relationService) {
        this.relationService = relationService;
    }

    // ---------------------------------------------------------------- members

    @GetMapping("/members")
    public PageResponse<UserResponse> getMembers(
            @PathVariable Long groupId,
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        return relationService.getMembers(groupId, pageable);
    }

    @PutMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMember(@PathVariable Long groupId, @PathVariable Long userId) {
        relationService.addMember(groupId, userId);
    }

    @DeleteMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long groupId, @PathVariable Long userId) {
        relationService.removeMember(groupId, userId);
    }

    // ------------------------------------------------------------------ roles

    @GetMapping("/roles")
    public List<RoleResponse> getRoles(@PathVariable Long groupId) {
        return relationService.getRoles(groupId);
    }

    @PutMapping("/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addRole(@PathVariable Long groupId, @PathVariable Long roleId) {
        relationService.addRole(groupId, roleId);
    }

    @DeleteMapping("/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRole(@PathVariable Long groupId, @PathVariable Long roleId) {
        relationService.removeRole(groupId, roleId);
    }

    /**
     * Replace the complete set of roles of the group.
     */
    @PutMapping("/roles")
    public List<RoleResponse> replaceRoles(@PathVariable Long groupId, @Valid @RequestBody IdSetRequest request) {
        return relationService.replaceRoles(groupId, request.ids());
    }
}
