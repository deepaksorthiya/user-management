package com.example.usermanagement.service;

import com.example.usermanagement.dto.group.GroupResponse;
import com.example.usermanagement.dto.role.RoleResponse;
import com.example.usermanagement.dto.user.UserAccessResponse;
import com.example.usermanagement.exception.ResourceNotFoundException;
import com.example.usermanagement.model.Permission;
import com.example.usermanagement.model.Role;
import com.example.usermanagement.model.User;
import com.example.usermanagement.model.UserGroup;
import com.example.usermanagement.repository.PermissionRepository;
import com.example.usermanagement.repository.RoleRepository;
import com.example.usermanagement.repository.UserGroupRepository;
import com.example.usermanagement.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** What a user is linked to: direct roles, group memberships and the resulting access. */
@Service
@Transactional(readOnly = true)
public class UserRelationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserGroupRepository groupRepository;
    private final PermissionRepository permissionRepository;

    public UserRelationService(UserRepository userRepository,
                               RoleRepository roleRepository,
                               UserGroupRepository groupRepository,
                               PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.groupRepository = groupRepository;
        this.permissionRepository = permissionRepository;
    }

    // ------------------------------------------------------------------ roles

    public List<RoleResponse> getRoles(Long userId) {
        requireUser(userId);
        return roleRepository.findDirectByUserId(userId).stream().map(RoleResponse::from).toList();
    }

    /** Idempotent: assigning an already assigned role is a no-op. */
    @Transactional
    public void assignRole(Long userId, Long roleId) {
        User user = getUser(userId);
        user.getRoles().add(getRole(roleId));
    }

    /** Idempotent: removing a role that is not assigned is a no-op. */
    @Transactional
    public void removeRole(Long userId, Long roleId) {
        User user = getUser(userId);
        user.getRoles().remove(getRole(roleId));
    }

    @Transactional
    public List<RoleResponse> replaceRoles(Long userId, Set<Long> roleIds) {
        User user = getUser(userId);
        Set<Role> roles = Lookups.loadAll(roleRepository, roleIds, "Role");
        user.getRoles().retainAll(roles);
        user.getRoles().addAll(roles);
        return roles.stream()
                .map(RoleResponse::from)
                .sorted(Comparator.comparing(RoleResponse::name))
                .toList();
    }

    // ----------------------------------------------------------------- groups

    public List<GroupResponse> getGroups(Long userId) {
        requireUser(userId);
        return groupRepository.findByUserId(userId).stream().map(GroupResponse::from).toList();
    }

    @Transactional
    public void joinGroup(Long userId, Long groupId) {
        User user = getUser(userId);
        user.getUserGroups().add(getGroup(groupId));
    }

    @Transactional
    public void leaveGroup(Long userId, Long groupId) {
        User user = getUser(userId);
        user.getUserGroups().remove(getGroup(groupId));
    }

    @Transactional
    public List<GroupResponse> replaceGroups(Long userId, Set<Long> groupIds) {
        User user = getUser(userId);
        Set<UserGroup> groups = Lookups.loadAll(groupRepository, groupIds, "Group");
        user.getUserGroups().retainAll(groups);
        user.getUserGroups().addAll(groups);
        return groups.stream()
                .map(GroupResponse::from)
                .sorted(Comparator.comparing(GroupResponse::name))
                .toList();
    }

    // ----------------------------------------------------------------- access

    /** Flattens direct roles + group roles into the user's final permission set. */
    public UserAccessResponse getAccess(Long userId) {
        User user = getUser(userId);

        List<String> groups = groupRepository.findByUserId(userId).stream().map(UserGroup::getName).toList();
        List<String> directRoles = roleRepository.findDirectByUserId(userId).stream().map(Role::getName).toList();
        List<String> groupRoles = roleRepository.findViaGroupsByUserId(userId).stream().map(Role::getName).toList();

        Set<String> permissions = new TreeSet<>();
        permissionRepository.findDirectByUserId(userId).stream().map(Permission::getName).forEach(permissions::add);
        permissionRepository.findViaGroupsByUserId(userId).stream().map(Permission::getName).forEach(permissions::add);

        return new UserAccessResponse(
                user.getId(), user.getUsername(), user.getActive(),
                groups, directRoles, groupRoles, List.copyOf(permissions));
    }

    // ---------------------------------------------------------------- helpers

    private void requireUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw ResourceNotFoundException.of("User", id);
        }
    }

    private User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    private Role getRole(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Role", id));
    }

    private UserGroup getGroup(Long id) {
        return groupRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Group", id));
    }
}
