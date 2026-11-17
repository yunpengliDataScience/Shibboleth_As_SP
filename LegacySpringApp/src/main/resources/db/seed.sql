-- Executed on every application startup. INSERT only if missing;
-- existing users, enabled flags, and role assignments are never overwritten.
INSERT INTO app_users (username, enabled)
SELECT 'yunpeng.li', TRUE WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE username = 'yunpeng.li');
INSERT INTO app_users (username, enabled)
SELECT 'luan.vu', TRUE WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE username = 'luan.vu');
INSERT INTO app_users (username, enabled)
SELECT 'jane.doe', TRUE WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE username = 'jane.doe');

-- Spring Security expects ROLE_ prefixes for hasRole('ADMIN') checks.
INSERT INTO app_roles (username, authority)
SELECT 'yunpeng.li', 'ROLE_USER' WHERE NOT EXISTS (SELECT 1 FROM app_roles WHERE username = 'yunpeng.li' AND authority = 'ROLE_USER');
INSERT INTO app_roles (username, authority)
SELECT 'yunpeng.li', 'ROLE_ADMIN' WHERE NOT EXISTS (SELECT 1 FROM app_roles WHERE username = 'yunpeng.li' AND authority = 'ROLE_ADMIN');
INSERT INTO app_roles (username, authority)
SELECT 'luan.vu', 'ROLE_USER' WHERE NOT EXISTS (SELECT 1 FROM app_roles WHERE username = 'luan.vu' AND authority = 'ROLE_USER');
INSERT INTO app_roles (username, authority)
SELECT 'luan.vu', 'ROLE_ADMIN' WHERE NOT EXISTS (SELECT 1 FROM app_roles WHERE username = 'luan.vu' AND authority = 'ROLE_ADMIN');
INSERT INTO app_roles (username, authority)
SELECT 'jane.doe', 'ROLE_USER' WHERE NOT EXISTS (SELECT 1 FROM app_roles WHERE username = 'jane.doe' AND authority = 'ROLE_USER');
