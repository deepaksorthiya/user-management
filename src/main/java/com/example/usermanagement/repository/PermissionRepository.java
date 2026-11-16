package com.example.usermanagement.repository;

import com.example.usermanagement.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<Permission> findAllByOrderByNameAsc();

    /**
     * Permissions granted directly to a group.
     */
    @Query("select p from UserGroup g join g.permissions p where g.id = :groupId order by p.name")
    List<Permission> findByGroupId(@Param("groupId") Long groupId);

    /**
     * Permissions granted by a role.
     */
    @Query("select p from Role r join r.permissions p where r.id = :roleId order by p.name")
    List<Permission> findByRoleId(@Param("roleId") Long roleId);

    /**
     * Effective permission names of a user in ONE round trip, from three sources:
     * (1) roles assigned to the user, (2) roles assigned to the user's groups,
     * (3) permissions granted directly to the user's groups. UNION de-duplicates in the database,
     * and no Permission entity is ever hydrated. Plain SQL, portable across H2 and PostgreSQL.
     */
    @Query(value = """
            select p.name as name
              from permissions p
              join role_permissions rp on rp.permission_id = p.id
              join user_roles ur on ur.role_id = rp.role_id
             where ur.user_id = :userId
            union
            select p.name as name
              from permissions p
              join role_permissions rp on rp.permission_id = p.id
              join group_roles gr on gr.role_id = rp.role_id
              join group_members gm on gm.group_id = gr.group_id
             where gm.user_id = :userId
            union
            select p.name as name
              from permissions p
              join group_permissions gpm on gpm.permission_id = p.id
              join group_members gm on gm.group_id = gpm.group_id
             where gm.user_id = :userId
            order by name
            """, nativeQuery = true)
    List<String> findEffectiveNamesByUserId(@Param("userId") Long userId);
}
