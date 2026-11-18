package com.example.usermanagement.service;

import com.example.usermanagement.dto.common.PageResponse;
import com.example.usermanagement.dto.group.GroupResponse;
import com.example.usermanagement.dto.role.RoleResponse;
import com.example.usermanagement.dto.user.UserResponse;
import com.example.usermanagement.exception.ResourceNotFoundException;
import com.example.usermanagement.repository.PermissionRepository;
import com.example.usermanagement.repository.RoleRepository;
import com.example.usermanagement.repository.UserGroupRepository;
import com.example.usermanagement.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only: the role -> permission link is owned by the role ({@link RoleRelationService}) and the
 * group -> permission link by the group ({@link GroupRelationService}). This side answers
 * "which roles / groups grant this permission?".
 */
@Service
@Transactional(readOnly = true)
public class PermissionRelationService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserGroupRepository groupRepository;
    private final UserRepository userRepository;

    public PermissionRelationService(PermissionRepository permissionRepository,
                                     RoleRepository roleRepository,
                                     UserGroupRepository groupRepository,
                                     UserRepository userRepository) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.groupRepository = groupRepository;
    }

    /**
     * Users that have this permission granted directly (paged).
     */
    public PageResponse<UserResponse> getUsers(Long permissionId, Pageable pageable) {
        requirePermission(permissionId);
        Sorts.require(pageable, Sorts.USER);
        return PageResponse.from(userRepository.findResponsesByPermissionId(permissionId, pageable));
    }

    /**
     * Groups that have this permission granted directly.
     */
    public List<GroupResponse> getGroups(Long permissionId) {
        requirePermission(permissionId);
        return groupRepository.findByPermissionsIdOrderByNameAsc(permissionId).stream()
                .map(GroupResponse::from)
                .toList();
    }

    private void requirePermission(Long permissionId) {
        if (!permissionRepository.existsById(permissionId)) {
            throw ResourceNotFoundException.of("Permission", permissionId);
        }
    }

    public List<RoleResponse> getRoles(Long permissionId) {
        requirePermission(permissionId);
        return roleRepository.findByPermissionsIdOrderByNameAsc(permissionId).stream()
                .map(RoleResponse::from)
                .toList();
    }
}
