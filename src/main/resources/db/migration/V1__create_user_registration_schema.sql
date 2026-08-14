CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    display_email VARCHAR(254) NOT NULL,
    canonical_email VARCHAR(254) NOT NULL,
    birth_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uk_users_canonical_email UNIQUE (canonical_email)
);

CREATE TABLE local_credentials (
    user_id BIGINT NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_local_credentials PRIMARY KEY (user_id),
    CONSTRAINT fk_local_credentials_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE user_consents (
    consent_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    consent_type VARCHAR(32) NOT NULL,
    consent_version VARCHAR(50) NOT NULL,
    agreed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_user_consents PRIMARY KEY (consent_id),
    CONSTRAINT ck_user_consents_type
        CHECK (consent_type IN ('TERMS', 'PERSONAL_INFORMATION', 'HEALTH_INFORMATION')),
    CONSTRAINT uk_user_consents_user_type_version
        UNIQUE (user_id, consent_type, consent_version),
    CONSTRAINT fk_user_consents_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE email_verification_proofs (
    proof_hash BINARY(32) NOT NULL,
    display_email VARCHAR(254) NOT NULL,
    canonical_email VARCHAR(254) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_email_verification_proofs PRIMARY KEY (proof_hash),
    CONSTRAINT uk_email_verification_proofs_canonical_email UNIQUE (canonical_email),
    INDEX ix_email_verification_proofs_expires_at (expires_at)
);
