# user-registration - Design Document

> **Superseded Notice (`AUTH-SCOPE-REDUCTION-002`)**: The signup `verificationProof` consumption contract is replaced by direct `email` and `verificationCode` validation. This document remains unchanged as a historical record of the implementation completed at that time.

> **Summary**: Small, transaction-focused account registration for the personally deployed Nyamlog toy project. <br>
> **Project**: Nyamlog <br>
> **Version**: 2.0.2 <br>
> **Date**: 2026-08-14 <br>
> **Status**: Approved - implementation realignment pending <br>
> **Plan**: `docs/01-plan/features/user-registration.plan.md` <br>
> **Current Decision**: `USER-REGISTRATION-009`

---

## 1. Design Goal

Make the registration transaction easy for a junior developer to read and explain:

```text
POST /api/v1/auth/signup
  -> request validation
  -> one service transaction
  -> lock and validate proof
  -> validate age, consent, and password
  -> save user, credential, and consents
  -> delete proof
  -> commit or rollback together
```

The design does not optimize for unrestricted signup traffic, commercial identity operations, or exhaustive adversarial conditions.

## 2. Feature Boundary

- `email-verification` creates and sends a challenge through Mailpit, confirms the code, and issues the signup proof.
- `user-registration` consumes the proof and creates the local account atomically.
- `local-login` verifies stored credentials and issues the Access Token.
- The developer may deploy and use the complete flow personally and invite a few acquaintances to register voluntarily for trial and feedback.
- Unrestricted public-user service operation, production mail, password recovery, social-provider callback logic, and account-deletion internals are outside this Design.
- Social signup/login remains a core project method under `FOUNDATION-006-R1`, but a separate `social-login` feature owns provider integration and first-social-login account creation.

## 3. API Contract

### 3.1 Endpoint

`POST /api/v1/auth/signup`

### 3.2 Request

| Field | Type | Rule |
|-------|------|------|
| `verificationProof` | string | Required opaque proof; exact transport format follows the shared email-verification contract |
| `password` | string | At least 8 characters and no more than 72 UTF-8 bytes |
| `birthDate` | date | Required; user must be age 19 or older |
| `consents` | array | Current `TERMS`, `PERSONAL_INFORMATION`, and `HEALTH_INFORMATION` once each |

Email is not repeated in the final request. It comes from the proof.

### 3.3 Success

- HTTP `201 Created`
- Application code `SIGNUP_COMPLETED`
- Response data contains the display email only
- No Access Token or automatic login

### 3.4 Errors

| HTTP | Application code | Meaning |
|------|------------------|---------|
| 400 | `INVALID_INPUT` | Missing or unreadable request fields |
| 409 | `EMAIL_ALREADY_REGISTERED` | Canonical email already exists |
| 422 | `EMAIL_VERIFICATION_INVALID` | Proof is malformed, missing, expired, or consumed |
| 422 | `UNDERAGE_NOT_ALLOWED` | Age rule not met |
| 422 | `REQUIRED_CONSENT_MISSING` | Required consent set invalid |
| 422 | `PASSWORD_POLICY_VIOLATION` | Basic password length/byte rule not met |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected failure without internal details |

`PASSWORD_COMPROMISED` is removed from the current public contract.

## 4. Data Model

### 4.1 `users`

| Column | Rule |
|--------|------|
| `user_id` | `BIGINT` identity primary key |
| `display_email` | Required display value |
| `canonical_email` | Required unique comparison value |
| `birth_date` | Required |
| `created_at` | Required |

### 4.2 `local_credentials`

| Column | Rule |
|--------|------|
| `user_id` | Primary and foreign key to `users`; cascade on account removal |
| `password_hash` | Complete delegating BCrypt value |
| `created_at` | Required |

### 4.3 `user_consents`

| Column | Rule |
|--------|------|
| `consent_id` | `BIGINT` identity primary key |
| `user_id` | Foreign key to `users`; cascade on account removal |
| `consent_type` | One of the three current types |
| `consent_version` | Required version string |
| `agreed_at` | Required |

### 4.4 `email_verification_proofs`

The shared handoff table stores only a proof digest plus display/canonical email and expiry. The raw proof is never persisted or logged. One successful signup deletes the row in the same transaction.

The existing V1 schema already represents this minimal data boundary. No schema change is required by this scope reduction.

The V1 registration schema does not need speculative social columns. The later `social-login` feature adds only the provider-subject persistence it actually needs through a forward Flyway Migration and reuses the same internal `users` identity.

## 5. Password Design

- Require at least 8 Java characters.
- Reject values whose UTF-8 encoding exceeds BCrypt's 72-byte boundary.
- Encode once with the configured Spring Security `PasswordEncoder`.
- Store the complete `{bcrypt}` value.
- Never trim, log, echo, or document a real submitted password.
- Do not require NFC normalization, malformed-surrogate-specific rules, a 15-code-point minimum, character-class composition, or a compromised-password blocklist.

This is a basic secure-storage exercise, not a production password-policy system.

## 6. Transaction Design

The public service method is the transaction boundary. A straightforward implementation is preferred:

1. Hash or otherwise resolve the submitted proof key.
2. Load the proof using a write lock.
3. Reject a missing or expired proof.
4. Validate age, consents, and password.
5. Check canonical-email uniqueness.
6. Encode the password.
7. Insert user, credential, and consent rows.
8. Delete the proof.
9. Commit.

If any persistence operation fails, the account rows and proof deletion roll back together.

Required correctness:

- database unique constraint remains the final duplicate-email guard
- a consumed proof cannot create another account
- a failed transaction does not leave partial account data
- failure does not consume a proof that should be retried

Not required:

- a separate orchestration/persistence service solely to keep BCrypt outside the transaction
- a non-locking proof pre-read followed by a second locked read
- multiple duplicate-email prechecks
- parsing a Hibernate cause chain to match one exact constraint name
- deterministic multi-thread race orchestration
- proof-replacement behavior as a registration completion gate

## 7. Component Boundary

Prefer the following minimum:

- `UserRegistrationController`
- request/response DTOs
- `UserRegistrationService` with the visible transaction
- JPA entities and repositories
- small age and consent helpers only if they remain clearer than inline private methods
- proof hashing helper if shared with `email-verification`
- password encoder configuration

The email canonicalization policy belongs to `email-verification` when that feature is implemented. It is not required as unused production code in `user-registration`.

## 8. OpenAPI and Swagger

- Generate the contract from the implemented Controller and DTOs.
- Keep detailed Korean descriptions for operation purpose, fields, validation rules, and public errors.
- Mark password and verification proof `writeOnly`.
- Do not supply password, proof, token, or credential examples.
- Keep OpenAPI disabled by default.
- When explicitly enabled in local development or a protected small deployment, Swagger Try it out is allowed for the developer or invited trial users.
- Do not expose Swagger without reasonable access restriction on an internet-reachable deployment.

## 9. Representative Tests

### Required

1. Basic password boundary, age boundary, and valid consent set
2. Controller success and representative invalid/business responses
3. Fresh MySQL schema and successful signup
4. Real MySQL rollback with no partial user, credential, or consent data
5. Successful proof consumption and replay rejection
6. Canonical-email unique constraint
7. OpenAPI sensitive-field and Korean-description contract

### Optional

- leap-day and malformed-surrogate permutations
- every service validation-order interaction
- blocklist resource availability and matching
- proof replacement
- same-proof concurrency executor/latch test
- every MySQL check/foreign-key constraint in the registration endpoint suite
- exact constraint-name classification test
- separate logging test when the request object's redaction is already covered by focused security review

Obsolete tests are removed or rewritten when the approved behavior changes. Core rollback and security tests must not be weakened merely to make a build pass.

## 10. Deployment Boundary

Registration must work in the small deployment used by the developer and optional invited acquaintances with:

- secrets supplied outside the repository
- persistent MySQL data
- HTTPS or platform-provided secure access when reachable over the internet
- Swagger disabled by default and enabled only for an access-restricted test session

Rate-limit clusters, multi-region identity storage, security monitoring pipelines, automated credential rotation, and public signup abuse controls are not required for this toy project.

## 11. `USER-REGISTRATION-009` - Toy-Project Simplification

**Status:** Approved 2026-08-14 <br>
**Reason:** Preserve the transaction learning goal while removing commercial-service and exhaustive-verification complexity.

**Supersedes:**

- `AUTH-003` password normalization, 15-code-point minimum, and malformed-Unicode clauses for this toy-project signup; the 72-byte BCrypt safety boundary remains
- all of `AUTH-004` local Top-100k blocklist requirements
- `AUTH-005` validation-order and pre-transaction optimization details while preserving verified proof and atomic account creation
- `USER-REGISTRATION-004` proof replacement and non-locking prevalidation requirements while preserving hashed, expiring, one-time transactional proof consumption
- the exhaustive portions of `USER-REGISTRATION-006`
- the Try it out prohibition in `USER-REGISTRATION-007`
- `USER-REGISTRATION-008` concurrency and exact constraint-classification completion requirements

## 12. Implementation Realignment

The current implementation matched the older Design before this decision. Before Report, it must be checked against this simplified target. Expected changes include:

- remove the large password blocklist resource and its production component
- simplify the password policy and public error set
- remove unused registration-owned email normalization code until `email-verification` needs it
- simplify duplicate/proof prechecks and transaction service structure where doing so improves clarity
- enable Try it out only when OpenAPI is explicitly enabled
- retain the transaction, proof consumption, BCrypt, database constraints, safe responses, and representative tests
- update or remove tests that belong only to superseded behavior

## 13. Remaining Design Decisions

None. Implementation simplification and re-analysis are required before Report.

## 14. Decision History

| Decision | Current status |
|----------|----------------|
| `USER-REGISTRATION-002` | Retained schema |
| `USER-REGISTRATION-003` | Retained API boundary except removed password-compromised error |
| `USER-REGISTRATION-004` | Partially superseded; core one-time transaction retained |
| `USER-REGISTRATION-005` | Retained BCrypt encoder |
| `USER-REGISTRATION-006` | Superseded by representative verification |
| `USER-REGISTRATION-007` | Retained documentation; Try it out rule superseded |
| `USER-REGISTRATION-008` | Superseded as a completion gate |
| `USER-REGISTRATION-009` | Approved current Design |

## Version History

| Version | Date | Change |
|---------|------|--------|
| 2.0.2 | 2026-08-14 | Adopted `FOUNDATION-006-R1`: preserved social signup/login as a separate feature without expanding local registration |
| 2.0.1 | 2026-08-14 | Clarified that a few acquaintances may voluntarily register and provide feedback without creating a public service requirement |
| 2.0.0 | 2026-08-14 | Replaced the operations-oriented registration Design with the transaction-focused toy-project contract in `USER-REGISTRATION-009` |
| 1.2.0 | 2026-08-14 | Historical Design before the toy-project scope reset |
