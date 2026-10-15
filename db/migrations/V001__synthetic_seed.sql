-- V001 synthetic configuration. Run only after manage.py conflict checks.
-- No account, password, personal information or production data is seeded.
SET time_zone = '+00:00';
START TRANSACTION;
INSERT INTO sys_dept (id, parent_id, name, status, created_at, updated_at)
VALUES (1001, NULL, '合成根部门', 'ENABLED', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO sys_role (id, parent_id, name, status, created_at, updated_at)
VALUES (1001, NULL, '平台管理员', 'ENABLED', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
       (1002, NULL, '相关部门', 'ENABLED', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO sys_resource
    (id, parent_id, name, resource_type, sort_order, route_name, route_path,
     component, icon, visible, status, permission_code, created_at, updated_at)
VALUES (1001, NULL, '框架说明', 'MENU', 10, 'FrameworkHome', '/home',
        'Home', 'House', 1, 'ENABLED', 'framework:home:view',
        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO sys_role_resource (role_id, resource_id) VALUES (1001, 1001);
COMMIT;
