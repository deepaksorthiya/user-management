package com.example.usermanagement.repository;

import com.example.usermanagement.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<Role> findAllByOrderByNameAsc();

    /** Roles that contain the permission. */
    List<Role> findByPermissionsIdOrderByNameAsc(Long permissionId);

    /** Roles assigned directly to the user. */
    @Query("select r from User u join u.roles r where u.id = :userId order by r.name")
    List<Role> findDirectByUserId(@Param("userId") Long userId);

    /** Roles assigned to a group. */
    @Query("select r from UserGroup g join g.roles r where g.id = :groupId order by r.name")
    List<Role> findByGroupId(@Param("groupId") Long groupId);

    /** Roles the user inherits from the groups they belong to. */
    @Query("select distinct r from User u join u.userGroups g join g.roles r where u.id = :userId order by r.name")
    List<Role> findViaGroupsByUserId(@Param("userId") Long userId);
}
