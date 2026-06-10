-- V5: User preferences — verbosity, answer format, default product/version

CREATE TABLE IF NOT EXISTS user_preferences (
    user_id         UUID        PRIMARY KEY,
    verbosity       VARCHAR(20) NOT NULL DEFAULT 'BALANCED',
    answer_format   VARCHAR(20) NOT NULL DEFAULT 'PROSE',
    default_product VARCHAR(100),
    default_version VARCHAR(50),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);
