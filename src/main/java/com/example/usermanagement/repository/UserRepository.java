package com.example.usermanagement.repository;

import com.example.usermanagement.dto.user.UserResponse;
import com.example.usermanagement.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, UserSearchRepository {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    /**
     * Two columns instead of the whole row.
     */
    Optional<UserHeader> findHeaderById(Long id);

    /**
     * Users that have the role assigned directly (DTO projection, backed by idx_user_roles_role_id).
     */
    @Query(value = """
            select new com.example.usermanagement.dto.user.UserResponse(
                u.id, u.username, u.email, u.firstName, u.lastName, u.active, u.createdAt, u.updatedAt)
            from User u join u.roles r
            where r.id = :roleId
            """,
            countQuery = "select count(u) from User u join u.roles r where r.id = :roleId")
    Page<UserResponse> findResponsesByRoleId(@Param("roleId") Long roleId, Pageable pageable);

    /**
     * Users that have the permission granted directly (DTO projection).
     */
    @Query(value = """
            select new com.example.usermanagement.dto.user.UserResponse(
                u.id, u.username, u.email, u.firstName, u.lastName, u.active, u.createdAt, u.updatedAt)
            from User u join u.permissions p
            where p.id = :permissionId
            """,
            countQuery = "select count(u) from User u join u.permissions p where p.id = :permissionId")
    Page<UserResponse> findResponsesByPermissionId(@Param("permissionId") Long permissionId, Pageable pageable);

    /**
     * Members of a group (DTO projection, backed by the group_members primary key).
     */
    @Query(value = """
            select new com.example.usermanagement.dto.user.UserResponse(
                u.id, u.username, u.email, u.firstName, u.lastName, u.active, u.createdAt, u.updatedAt)
            from User u join u.userGroups g
            where g.id = :groupId
            """,
            countQuery = "select count(u) from User u join u.userGroups g where g.id = :groupId")
    Page<UserResponse> findResponsesByGroupId(@Param("groupId") Long groupId, Pageable pageable);
}
