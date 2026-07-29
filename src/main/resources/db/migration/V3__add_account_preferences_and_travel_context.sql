CREATE TABLE account_preferences (
    account_id BIGINT UNSIGNED NOT NULL,
    preferred_language VARCHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'zh-CN',
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (account_id),
    CONSTRAINT fk_account_preferences_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE,
    CONSTRAINT chk_account_preferences_language CHECK (preferred_language IN ('zh-CN', 'en'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_travel_context (
    account_id BIGINT UNSIGNED NOT NULL,
    country_or_region VARCHAR(100) NULL,
    city VARCHAR(100) NULL,
    trip_start_date DATE NULL,
    trip_end_date DATE NULL,
    assistance_needs VARCHAR(1000) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (account_id),
    CONSTRAINT fk_account_travel_context_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE,
    CONSTRAINT chk_account_travel_context_dates CHECK (
        trip_start_date IS NULL OR trip_end_date IS NULL OR trip_end_date >= trip_start_date
    ),
    CONSTRAINT chk_account_travel_context_country CHECK (
        country_or_region IS NULL OR CHAR_LENGTH(TRIM(country_or_region)) BETWEEN 1 AND 100
    ),
    CONSTRAINT chk_account_travel_context_city CHECK (
        city IS NULL OR CHAR_LENGTH(TRIM(city)) BETWEEN 1 AND 100
    ),
    CONSTRAINT chk_account_travel_context_assistance CHECK (
        assistance_needs IS NULL OR CHAR_LENGTH(TRIM(assistance_needs)) BETWEEN 1 AND 1000
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_dietary_restriction (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    position TINYINT UNSIGNED NOT NULL,
    restriction_text VARCHAR(80) NOT NULL,
    normalized_text VARCHAR(80) COLLATE utf8mb4_0900_as_cs NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_dietary_position (account_id, position),
    UNIQUE KEY uk_account_dietary_normalized (account_id, normalized_text),
    CONSTRAINT fk_account_dietary_context FOREIGN KEY (account_id)
        REFERENCES account_travel_context (account_id) ON DELETE CASCADE,
    CONSTRAINT chk_account_dietary_position CHECK (position < 20),
    CONSTRAINT chk_account_dietary_text CHECK (CHAR_LENGTH(TRIM(restriction_text)) BETWEEN 1 AND 80),
    CONSTRAINT chk_account_dietary_normalized CHECK (CHAR_LENGTH(normalized_text) BETWEEN 1 AND 80)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO account_preferences (account_id, preferred_language, updated_at)
SELECT id, 'zh-CN', created_at FROM account;

INSERT INTO account_travel_context (account_id, version, created_at, updated_at)
SELECT id, 0, created_at, created_at FROM account;
