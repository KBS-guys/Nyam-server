CREATE TABLE email_verification_challenges (
    canonical_email VARCHAR(254) NOT NULL,
    display_email VARCHAR(254) NOT NULL,
    code_verifier BINARY(32) NOT NULL,
    verification_started_at DATETIME(6) NOT NULL,
    code_issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    resend_count TINYINT UNSIGNED NOT NULL,
    failed_attempt_count TINYINT UNSIGNED NOT NULL,
    CONSTRAINT pk_email_verification_challenges PRIMARY KEY (canonical_email),
    CONSTRAINT ck_email_verification_challenges_resend_count
        CHECK (resend_count BETWEEN 0 AND 3),
    CONSTRAINT ck_email_verification_challenges_failed_attempt_count
        CHECK (failed_attempt_count BETWEEN 0 AND 5),
    CONSTRAINT ck_email_verification_challenges_issue_order
        CHECK (code_issued_at >= verification_started_at),
    CONSTRAINT ck_email_verification_challenges_expiry_order
        CHECK (expires_at > code_issued_at)
);
