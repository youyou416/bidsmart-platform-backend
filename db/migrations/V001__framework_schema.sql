-- V001: framework tables only. Target: explicitly confirmed hr_score_dev.
-- Run through db/tools/manage.py; this file never creates a database or tenant.
-- DDL is not assumed transactional. No existing table is altered or dropped.
SET time_zone = '+00:00';

CREATE TABLE sys_dept (
    id BIGINT UNSIGNED NOT NULL,
    parent_id BIGINT UNSIGNED NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_dept_parent (parent_id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE sys_role (
    id BIGINT UNSIGNED NOT NULL,
    parent_id BIGINT UNSIGNED NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_role_parent (parent_id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE sys_resource (
    id BIGINT UNSIGNED NOT NULL,
    parent_id BIGINT UNSIGNED NULL,
    name VARCHAR(128) NOT NULL,
    resource_type VARCHAR(16) NOT NULL,
    sort_order INT NOT NULL,
    route_name VARCHAR(128) NULL,
    route_path VARCHAR(255) NULL,
    component VARCHAR(64) NULL,
    icon VARCHAR(64) NULL,
    visible TINYINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    permission_code VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_resource_parent (parent_id),
    KEY idx_resource_permission (permission_code)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE sys_user (
    id BIGINT UNSIGNED NOT NULL,
    username VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    dept_id BIGINT UNSIGNED NULL,
    identity_type VARCHAR(32) NOT NULL DEFAULT 'BUYER',
    enterprise_id BIGINT UNSIGNED NULL,
    data_scope_type VARCHAR(32) NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL,
    password_change_required TINYINT NOT NULL,
    password_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_dept (dept_id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE sys_user_role (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE sys_role_resource (
    role_id BIGINT UNSIGNED NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, resource_id),
    KEY idx_role_resource_resource (resource_id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE sys_user_scope_dept (
    user_id BIGINT UNSIGNED NOT NULL,
    dept_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, dept_id),
    KEY idx_scope_dept_dept (dept_id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE sys_user_purchase_type (
    user_id BIGINT UNSIGNED NOT NULL,
    purchase_type_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, purchase_type_code)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE sys_user_material_scope (
    user_id BIGINT UNSIGNED NOT NULL,
    material_category_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, material_category_code)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE sys_login_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(48) NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    outcome VARCHAR(24) NOT NULL,
    source_ip VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    event_time DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_login_event_time (event_time)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE sys_operation_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(48) NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    outcome VARCHAR(24) NOT NULL,
    source_ip VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    event_time DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_operation_event_time (event_time)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
