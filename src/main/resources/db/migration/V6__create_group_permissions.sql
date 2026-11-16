-- group <-> permission: a permission granted directly to a group, so every member of the group
-- gets it without needing a role. (Roles assigned to the group keep working as before.)
-- ON DELETE CASCADE: deleting a group or a permission cleans up the assignment.
CREATE TABLE group_permissions
(
    group_id      BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    CONSTRAINT pk_group_permissions PRIMARY KEY (group_id, permission_id),
    CONSTRAINT fk_group_permissions_group FOREIGN KEY (group_id) REFERENCES user_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);
-- "which groups have this permission?" and the cascade from permission deletes
CREATE INDEX idx_group_permissions_permission_id ON group_permissions (permission_id);
