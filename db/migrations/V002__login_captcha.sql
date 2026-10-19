-- Run once after V001 in the intended MySQL-compatible OceanBase database.
-- No plaintext answer, HTTP session ID, credentials or synthetic challenge is stored.
SET time_zone = '+00:00';

CREATE TABLE sys_login_captcha (
    captcha_id VARCHAR(64) NOT NULL,
    session_binding_hash VARCHAR(64) NOT NULL,
    answer_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    PRIMARY KEY (captcha_id),
    UNIQUE KEY uk_captcha_session (session_binding_hash),
    KEY idx_captcha_expires (expires_at)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
