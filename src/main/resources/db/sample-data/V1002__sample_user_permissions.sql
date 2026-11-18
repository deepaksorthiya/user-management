-- Sample: a permission granted directly to a user.
INSERT INTO user_permissions (user_id, permission_id)
SELECT u.id, p.id FROM users u JOIN permissions p ON p.name = 'REPORT_VIEW' WHERE u.username = 'dave';
