package com.example.usermanagement.service;

import com.example.usermanagement.dto.role.RoleResponse;
import com.example.usermanagement.exception.ResourceNotFoundException;
import com.example.usermanagement.repository.PermissionRepository;
import com.example.usermanagement.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only: the role -> permission link is owned by the role, so it is changed through
 * {@link RoleRelationService}. This side answers "which roles grant this permission?".
 */
@Service
@Transactional(readOnly = true)
public class PermissionRelationService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    public PermissionRelationService(PermissionRepository permissionRepository, RoleRepository roleRepository) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
    }

    public List<RoleResponse> getRoles(Long permissionId) {
        if (!permissionRepository.existsById(permissionId)) {
            throw ResourceNotFoundException.of("Permission", permissionId);
        }
        return roleRepository.findByPermissionsIdOrderByNameAsc(permissionId).stream()
                .map(RoleResponse::from)
                .toList();
    }
}
