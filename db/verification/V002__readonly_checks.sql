-- Read-only checks after V002, in the independently confirmed target database.
SELECT DATABASE(), VERSION(), @@version_comment, @@time_zone;
SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.TABLES
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_login_captcha';
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_login_captcha' ORDER BY ORDINAL_POSITION;
SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_login_captcha' ORDER BY INDEX_NAME, SEQ_IN_INDEX;
SELECT COUNT(*) AS total_challenges,
       COALESCE(SUM(expires_at<=UTC_TIMESTAMP(3)),0) AS expired_challenges FROM sys_login_captcha;
