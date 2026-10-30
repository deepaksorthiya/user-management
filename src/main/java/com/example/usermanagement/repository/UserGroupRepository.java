package com.example.usermanagement.repository;

import com.example.usermanagement.model.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserGroupRepository extends JpaRepository<UserGroup, Long> {

    Optional<UserGroup> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<UserGroup> findAllByOrderByNameAsc();

    /** Groups that have the role assigned. */
    List<UserGroup> findByRolesIdOrderByNameAsc(Long roleId);

    /** Groups the user is a member of. */
    @Query("select g from User u join u.userGroups g where u.id = :userId order by g.name")
    List<UserGroup> findByUserId(@Param("userId") Long userId);
}
