package com.example.usermanagement.service;

import com.example.usermanagement.dto.common.PageResponse;
import com.example.usermanagement.dto.permission.PermissionResponse;
import com.example.usermanagement.dto.role.RoleResponse;
import com.example.usermanagement.dto.user.UserResponse;
import com.example.usermanagement.exception.ResourceNotFoundException;
import com.example.usermanagement.model.Permission;
import com.example.usermanagement.model.Role;
import com.example.usermanagement.model.User;
import com.example.usermanagement.model.UserGroup;
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
 * What a group is linked to: its members, its roles and the permissions granted to it directly.
 */
@Service
@Transactional(readOnly = true)
public class GroupRelationService {

    private final UserGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public GroupRelationService(UserGroupRepository groupRepository,
                                UserRepository userRepository,
                                RoleRepository roleRepository,
                                PermissionRepository permissionRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    // ---------------------------------------------------------------- members

    public PageResponse<UserResponse> getMembers(Long groupId, Pageable pageable) {
        requireGroup(groupId);
        Sorts.require(pageable, Sorts.USER);
        return PageResponse.from(userRepository.findResponsesByGroupId(groupId, pageable));
    }

    @Transactional
    public void addMember(Long groupId, Long userId) {
        UserGroup group = getGroup(groupId);
        getUser(userId).getUserGroups().add(group);
    }

    @Transactional
    public void removeMember(Long groupId, Long userId) {
        UserGroup group = getGroup(groupId);
        getUser(userId).getUserGroups().remove(group);
    }

    // ------------------------------------------------------------------ roles

    public List<RoleResponse> getRoles(Long groupId) {
        requireGroup(groupId);
        return roleRepository.findByGroupId(groupId).stream().map(RoleResponse::from).toList();
    }

    @Transactional
    public void addRole(Long groupId, Long roleId) {
        getGroup(groupId).getRoles().add(getRole(roleId));
    }

    @Transactional
    public void removeRole(Long groupId, Long roleId) {
        getGroup(groupId).getRoles().remove(getRole(roleId));
    }

    @Transactional
    public List<RoleResponse> replaceRoles(Long groupId, Set<Long> roleIds) {
        UserGroup group = getGroup(groupId);
        Set<Role> roles = Lookups.loadAll(roleRepository, roleIds, "Role");
        group.getRoles().retainAll(roles);
        group.getRoles().addAll(roles);
        return roles.stream()
                .map(RoleResponse::from)
                .sorted(Comparator.comparing(RoleResponse::name))
                .toList();
    }

    // ------------------------------------------------------------ permissions

    /**
     * Permissions granted directly to the group (not the ones coming from its roles).
     */
    public List<PermissionResponse> getPermissions(Long groupId) {
        requireGroup(groupId);
        return permissionRepository.findByGroupId(groupId).stream().map(PermissionResponse::from).toList();
    }

    /**
     * Idempotent: granting an already granted permission is a no-op.
     */
    @Transactional
    public void addPermission(Long groupId, Long permissionId) {
        getGroup(groupId).getPermissions().add(getPermission(permissionId));
    }

    /**
     * Idempotent: revoking a permission that is not granted is a no-op.
     */
    @Transactional
    public void removePermission(Long groupId, Long permissionId) {
        getGroup(groupId).getPermissions().remove(getPermission(permissionId));
    }

    @Transactional
    public List<PermissionResponse> replacePermissions(Long groupId, Set<Long> permissionIds) {
        UserGroup group = getGroup(groupId);
        Set<Permission> permissions = Lookups.loadAll(permissionRepository, permissionIds, "Permission");
        group.getPermissions().retainAll(permissions);
        group.getPermissions().addAll(permissions);
        return permissions.stream()
                .map(PermissionResponse::from)
                .sorted(Comparator.comparing(PermissionResponse::name))
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private void requireGroup(Long id) {
        if (!groupRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Group", id);
        }
    }

    private UserGroup getGroup(Long id) {
        return groupRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Group", id));
    }

    private User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    private Permission getPermission(Long id) {
        return permissionRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Permission", id));
    }

    private Role getRole(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Role", id));
    }
}
