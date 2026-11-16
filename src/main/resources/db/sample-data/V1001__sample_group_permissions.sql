-- Sample: permissions granted directly to groups (in addition to the roles from V1000).
INSERT INTO group_permissions (group_id, permission_id)
SELECT g.id, p.id FROM user_groups g JOIN permissions p ON p.name = 'REPORT_VIEW' WHERE g.name = 'Engineering';

INSERT INTO group_permissions (group_id, permission_id)
SELECT g.id, p.id FROM user_groups g JOIN permissions p ON p.name = 'GROUP_READ' WHERE g.name = 'Support Team';
