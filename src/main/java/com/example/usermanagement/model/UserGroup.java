package com.example.usermanagement.model;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/**
 * A group of users. Named UserGroup (table user_groups) because GROUP / GROUPS are
 * reserved words in SQL and HQL. Members inherit every role assigned to the group.
 */
@Entity
@Table(name = "user_groups")
public class UserGroup extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    /**
     * Owning side of group <-> role. Membership (user <-> group) is owned by User.
     */
    @ManyToMany
    @JoinTable(
            name = "group_roles",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public UserGroup() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    @Override
    public String toString() {
        return "UserGroup{id=" + getId() + ", name='" + name + "'}";
    }
}
