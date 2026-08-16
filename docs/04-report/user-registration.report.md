# user-registration Completion Report

> **Status**: Complete <br>
> **Project**: Nyamlog <br>
> **Completion Date**: 2026-08-15 <br>
> **Current Decision**: `USER-REGISTRATION-009`

---

## 1. Summary

| Item | Content |
|------|---------|
| Feature | `user-registration` |
| Start Date | 2026-08-08 |
| End Date | 2026-08-15 |
| Scope | Final local email/password signup after email verification |
| Result | Complete against Plan and Design 2.0.2 |

### Results

```text
Completion Rate: 100%
Design Match Rate: 100% (28/28)
Functional Requirements: 11/11 complete
Automated Tests: 38 passed, 0 failed, 0 errors, 0 skipped
```

This feature provides the final local-account creation transaction. It consumes
an email-verification proof, validates age, required consents, and password policy,
stores the user, BCrypt credential, and consent rows, and consumes the proof in
one transaction. It deliberately does not send email, issue login tokens, or
implement social-provider callbacks.

## 2. Related Documents

| Phase | Document | Status |
|-------|----------|--------|
| Plan | [`user-registration.plan.md`](../01-plan/features/user-registration.plan.md) | Approved 2.0.2 |
| Design | [`user-registration.design.md`](../02-design/features/user-registration.design.md) | Approved 2.0.2 |
| Analysis | [`user-registration.analysis.md`](../03-analysis/user-registration.analysis.md) | Complete 1.1.0 |

## 3. Completed Items

### 3.1 Functional Requirements

| ID | Requirement | Status | Evidence |
|----|-------------|--------|----------|
| UR-01 | Accept proof, password, birth date, and required consents | Complete | Request DTO and controller tests |
| UR-02 | Reject missing, malformed, expired, or consumed proof safely | Complete | Proof hasher, locked lookup, web and MySQL tests |
| UR-03 | Enforce the age-19 rule | Complete | `AgePolicy` boundary tests |
| UR-04 | Require the three current consent types once each | Complete | `ConsentPolicy` tests and MySQL persistence |
| UR-05 | Enforce at least 8 characters and at most 72 UTF-8 bytes | Complete | `PasswordPolicyTest` |
| UR-06 | Store only encoded BCrypt credentials | Complete | Password configuration and MySQL success test |
| UR-07 | Enforce canonical-email uniqueness and return safe conflict | Complete | Service precheck and MySQL UNIQUE evidence |
| UR-08 | Save account data and consume proof in one transaction | Complete | `UserRegistrationService` and integration tests |
| UR-09 | Roll back partial writes and preserve proof after failure | Complete | Actual-MySQL rollback and retry test |
| UR-10 | Return `201 SIGNUP_COMPLETED` without token issuance | Complete | Controller and MySQL response tests |
| UR-11 | Document the API and public errors in Korean Swagger | Complete | OpenAPI contract test |

### 3.2 Data and API Impact

- Added the first Flyway application schema for `users`, `local_credentials`,
  `user_consents`, and `email_verification_proofs`.
- Added `POST /api/v1/auth/signup`.
- The response contains the display email only and does not expose internal IDs,
  tokens, canonical email, password, or verification proof.
- `verificationProof` and `password` are marked write-only in OpenAPI and have no examples.
- OpenAPI remains disabled by default and Swagger Try it out becomes available only
  when `NYAM_OPENAPI_ENABLED=true` is supplied intentionally.

### 3.3 Scope Simplification Completed

The final implementation applies `USER-REGISTRATION-009`:

- removed the large local compromised-password list
- removed NFC, malformed-surrogate, and 15-code-point password rules
- removed unused registration-owned email-normalization classes
- replaced split orchestration/persistence services with one visible transaction
- removed repeated proof and duplicate-email prechecks
- removed exact Hibernate constraint-name parsing
- retained proof locking, one-time consumption, BCrypt, UNIQUE constraints, rollback,
  secret redaction, and representative tests

## 4. Quality Metrics

| Metric | Target | Final | Status |
|--------|--------|-------|--------|
| Design Match Rate | At least 90% | 100% (28/28) | Pass |
| Automated Tests | Representative approved scenarios | 38/38 passed | Pass |
| Actual MySQL Transaction Tests | Success, rollback, replay, uniqueness | 5/5 passed | Pass |
| JavaDoc | Generated without error | `BUILD SUCCESSFUL` | Pass |
| Sensitive OpenAPI Fields | Write-only, no examples | Contract test passed | Pass |
| Test Coverage Percentage | Not defined as a completion target | Not measured | Not claimed |

Verification commands executed on 2026-08-15:

```text
.\gradlew.bat test
BUILD SUCCESSFUL
tests=38 failures=0 errors=0 skipped=0 suites=12

.\gradlew.bat javadoc
BUILD SUCCESSFUL
```

The Testcontainers-based `UserRegistrationMySqlIntegrationTest` executed five
tests against MySQL 8.4.5 with no skip.

## 5. Security and Integrity Review

- Plaintext passwords, raw proofs, hashes, tokens, and database credentials are not returned or logged.
- BCrypt's 72-byte input boundary is enforced before encoding.
- Raw verification proofs are hashed before persistence lookup.
- Pessimistic locking and transactional deletion prevent sequential proof replay.
- MySQL constraints remain the final guard for duplicate account and consent data.
- Persistence failure rolls back user, credential, consent, and proof changes together.
- Internal database and exception details are not included in client responses.

No unrestricted public identity-service, abuse-control, or commercial operations
claim is made by this report.

## 6. Lessons Learned

### 6.1 What Went Well

- An actual MySQL failure test makes the transaction boundary observable instead
  of relying only on mocked service tests.
- Separating `users` from `local_credentials` keeps the internal user identity
  reusable for the later social-login feature without speculative social columns.
- Proof-bound email prevents the final signup request from replacing the email
  that was actually verified.
- Korean OpenAPI descriptions and secret-field checks keep the API directly testable
  without exposing sensitive example values.

### 6.2 What Needed Improvement

- The initial registration scope included a large blocklist, detailed Unicode rules,
  deterministic concurrency orchestration, and exact exception-chain parsing that
  were disproportionate to a junior toy-project portfolio.
- Splitting validation and persistence across services made the central transaction
  harder to read and explain.
- Completion criteria should be time-boxed around representative business evidence
  before adding exhaustive edge-case tests.

### 6.3 What to Try Next

- Start each feature with the smallest executable vertical flow.
- Preserve tests for transaction rollback, ownership, and data integrity, while
  deferring speculative operational complexity.
- Move to the food and meal-recording transaction after the minimum authentication
  path is usable instead of expanding identity features indefinitely.

## 7. Known Boundaries

The following functions are not missing from this completed feature because they
are owned by separate planned features:

- `email-verification`: Mailpit delivery, code confirmation, proof issuance,
  simple resend and attempt limits
- `local-login`: credential verification, Access Token, protected API authentication,
  and minimal logout behavior
- `social-login`: one provider integration and first-login onboarding
- `account-deletion`: authenticated account removal flow

Until `email-verification` is implemented, automated tests can seed a valid proof,
but a person cannot yet begin the complete signup flow from email delivery in Swagger.

## 8. Next Steps

1. Plan and design the minimal `email-verification` feature.
2. Implement Mailpit delivery, code confirmation, and one-time proof issuance.
3. Verify the complete email-to-signup flow through Swagger.
4. Implement minimum local login and Access Token authentication.
5. Add one social signup/login provider as a separate bounded feature.

Archive is intentionally not performed by this report.

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-08-15 | Completed Report for `USER-REGISTRATION-009` with 100% match and 38 passing tests |
