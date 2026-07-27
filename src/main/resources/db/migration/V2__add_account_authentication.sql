CREATE TABLE account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_name VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_account_name VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_hash VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_public_id (public_id),
    UNIQUE KEY uk_account_normalized_name (normalized_account_name),
    CONSTRAINT chk_account_name_length CHECK (CHAR_LENGTH(account_name) BETWEEN 4 AND 20),
    CONSTRAINT chk_account_normalized_name_length CHECK (CHAR_LENGTH(normalized_account_name) BETWEEN 4 AND 20)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_auth_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    access_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    access_expires_at DATETIME(6) NOT NULL,
    absolute_expires_at DATETIME(6) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_session_public_id (public_id),
    UNIQUE KEY uk_auth_session_access_hash (access_token_hash),
    KEY idx_auth_session_account_status (account_id, status, absolute_expires_at),
    CONSTRAINT fk_auth_session_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT chk_auth_session_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT chk_auth_session_expiry CHECK (access_expires_at <= absolute_expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_refresh_token (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    rotated_at DATETIME(6) NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_session_status (session_id, status, expires_at),
    CONSTRAINT fk_refresh_token_session FOREIGN KEY (session_id) REFERENCES account_auth_session (id),
    CONSTRAINT chk_refresh_token_status CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_auth_failure_bucket (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    key_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    window_started_at DATETIME(6) NOT NULL,
    failure_count INT UNSIGNED NOT NULL,
    blocked_until DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_failure_key (key_type, key_hash),
    KEY idx_auth_failure_expiry (blocked_until, updated_at),
    CONSTRAINT chk_auth_failure_key_type CHECK (key_type IN ('ACCOUNT', 'IP')),
    CONSTRAINT chk_auth_failure_count CHECK (failure_count >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
