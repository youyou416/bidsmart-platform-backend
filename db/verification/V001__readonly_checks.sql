-- Read-only observations. Run only in the explicitly confirmed hr_score_dev.
-- These queries do not establish tenant ownership or MySQL compatibility mode.
SELECT DATABASE() AS selected_database,
       VERSION() AS product_version,
       @@version_comment AS product_comment,
       @@character_set_database AS database_charset,
       @@collation_database AS database_collation,
       @@time_zone AS session_time_zone;

SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('sys_user', 'sys_dept', 'sys_role', 'sys_resource',
    'sys_user_role', 'sys_role_resource', 'sys_user_scope_dept',
    'sys_user_purchase_type', 'sys_user_material_scope',
    'sys_login_log', 'sys_operation_log')
ORDER BY TABLE_NAME;

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('sys_user', 'sys_dept', 'sys_role', 'sys_resource',
    'sys_user_role', 'sys_role_resource', 'sys_user_scope_dept',
    'sys_user_purchase_type', 'sys_user_material_scope',
    'sys_login_log', 'sys_operation_log')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('sys_user', 'sys_dept', 'sys_role', 'sys_resource',
    'sys_user_role', 'sys_role_resource', 'sys_user_scope_dept',
    'sys_user_purchase_type', 'sys_user_material_scope',
    'sys_login_log', 'sys_operation_log')
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

SELECT '合成中文资料' AS synthetic_unicode,
       CAST(9007199254740993 AS UNSIGNED) AS long_id_above_js_safe_integer,
       UTC_TIMESTAMP(3) AS observed_utc_time;
