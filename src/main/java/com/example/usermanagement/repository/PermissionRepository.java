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

    /** Permissions granted by a role. */
    @Query("select p from Role r join r.permissions p where r.id = :roleId order by p.name")
    List<Permission> findByRoleId(@Param("roleId") Long roleId);

    /** Permissions the user gets from roles assigned directly. */
    @Query("select distinct p from User u join u.roles r join r.permissions p where u.id = :userId")
    List<Permission> findDirectByUserId(@Param("userId") Long userId);

    /** Permissions the user gets from roles of the groups they belong to. */
    @Query("select distinct p from User u join u.userGroups g join g.roles r join r.permissions p where u.id = :userId")
    List<Permission> findViaGroupsByUserId(@Param("userId") Long userId);
}
