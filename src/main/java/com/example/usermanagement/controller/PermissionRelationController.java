package com.example.usermanagement.controller;

import com.example.usermanagement.dto.role.RoleResponse;
import com.example.usermanagement.service.PermissionRelationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of a permission's relations. The role -> permission link is owned by the role,
 * so grant / revoke lives in {@link RoleRelationController}.
 */
@RestController
@RequestMapping("/api/v1/permissions/{permissionId}")
public class PermissionRelationController {

    private final PermissionRelationService relationService;

    public PermissionRelationController(PermissionRelationService relationService) {
        this.relationService = relationService;
    }

    /** Roles that grant this permission. */
    @GetMapping("/roles")
    public List<RoleResponse> getRoles(@PathVariable Long permissionId) {
        return relationService.getRoles(permissionId);
    }
}
