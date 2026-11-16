package com.example.usermanagement.service;

import com.example.usermanagement.dto.common.PageResponse;
import com.example.usermanagement.dto.group.GroupResponse;
import com.example.usermanagement.dto.permission.PermissionResponse;
import com.example.usermanagement.dto.user.UserResponse;
import com.example.usermanagement.exception.ResourceNotFoundException;
import com.example.usermanagement.model.Permission;
import com.example.usermanagement.model.Role;
import com.example.usermanagement.repository.PermissionRepository;
import com.example.usermanagement.repository.RoleRepository;
import com.example.usermanagement.repository.UserGroupRepository;
import com.example.usermanagement.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * What a role is linked to: its permissions, and who/what it is assigned to.
 */
@Service
@Transactional(readOnly = true)
public class RoleRelationService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final UserGroupRepository groupRepository;

    public RoleRelationService(RoleRepository roleRepository,
                               PermissionRepository permissionRepository,
                               UserRepository userRepository,
                               UserGroupRepository groupRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
    }

    // ------------------------------------------------------------ permissions

    public List<PermissionResponse> getPermissions(Long roleId) {
        requireRole(roleId);
        return permissionRepository.findByRoleId(roleId).stream().map(PermissionResponse::from).toList();
    }

    @Transactional
    public void addPermission(Long roleId, Long permissionId) {
        getRole(roleId).getPermissions().add(getPermission(permissionId));
    }

    @Transactional
    public void removePermission(Long roleId, Long permissionId) {
        getRole(roleId).getPermissions().remove(getPermission(permissionId));
    }

    @Transactional
    public List<PermissionResponse> replacePermissions(Long roleId, Set<Long> permissionIds) {
        Role role = getRole(roleId);
        Set<Permission> permissions = Lookups.loadAll(permissionRepository, permissionIds, "Permission");
        role.getPermissions().retainAll(permissions);
        role.getPermissions().addAll(permissions);
        return permissions.stream()
                .map(PermissionResponse::from)
                .sorted(Comparator.comparing(PermissionResponse::name))
                .toList();
    }

    // ------------------------------------------------------- who has the role

    /**
     * Users the role is assigned to directly (not through a group).
     */
    public PageResponse<UserResponse> getUsers(Long roleId, Pageable pageable) {
        requireRole(roleId);
        Sorts.require(pageable, Sorts.USER);
        return PageResponse.from(userRepository.findResponsesByRoleId(roleId, pageable));
    }

    public List<GroupResponse> getGroups(Long roleId) {
        requireRole(roleId);
        return groupRepository.findByRolesIdOrderByNameAsc(roleId).stream().map(GroupResponse::from).toList();
    }

    // ---------------------------------------------------------------- helpers

    private void requireRole(Long id) {
        if (!roleRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Role", id);
        }
    }

    private Role getRole(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Role", id));
    }

    private Permission getPermission(Long id) {
        return permissionRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Permission", id));
    }
}
