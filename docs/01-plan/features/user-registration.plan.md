# user-registration - Plan Document

> **Summary**: Implement one understandable local-account signup transaction for the personally deployed Nyamlog toy project. <br>
> **Project**: Nyamlog <br>
> **Version**: 2.0.2 <br>
> **Date**: 2026-08-14 <br>
> **Status**: Approved - implementation simplification required before Report <br>
> **Current Decision**: `USER-REGISTRATION-009` <br>
> **Scope Authority**: `FOUNDATION-006`

---

## 1. Purpose

`user-registration` exists to demonstrate basic Spring Boot API design, relational persistence, password hashing, database constraints, and a service-layer transaction.

Nyamlog will be deployed for the developer's direct use, and a few acquaintances may voluntarily register to try it and provide feedback. Signup is not designed as a commercial identity service, unrestricted public signup system, or guaranteed ongoing service.

The feature is complete when a verified email can create one account, credential, and consent set atomically; invalid requests create no partial data; the API is documented in Korean Swagger; and representative tests pass.

## 2. In Scope

- `POST /api/v1/auth/signup`
- Signup identity derived from an email-verification proof
- Basic age and required-consent validation
- Basic password length validation and BCrypt encoding
- Canonical-email uniqueness enforced by MySQL
- Atomic creation of user, local credential, and consent rows
- Atomic deletion of the one-time verification proof on success
- Safe common success and error responses
- Korean Swagger documentation and explicitly enabled Try it out
- Representative unit, web, and actual-MySQL tests

## 3. Out of Scope

- Email sending, verification-code confirmation, resend, attempt limits, and proof issuance; owned by `email-verification`
- Login, Access Token, protected API authentication, and logout; owned by `local-login`
- Unrestricted public signup operations, production abuse-control infrastructure, device tracking, account recovery, and social-provider callback implementation
- A large breached-password dataset, scheduled blocklist updates, source pinning, or blocklist availability policy
- Production identity hardening, security monitoring, audit pipelines, and commercial compliance certification
- Deterministic concurrency harnesses, proof-replacement matrices, exact database exception-chain classification, and exhaustive validation-order tests
- Account deletion before the core account-to-meal flow is complete

Social signup/login remains in the overall Nyamlog scope through `FOUNDATION-006-R1`. It is outside only this local `user-registration` implementation and will be owned by a separate `social-login` feature so OAuth/provider logic does not inflate the email/password transaction.

## 4. Functional Requirements

| ID | Requirement |
|----|-------------|
| UR-01 | Accept a final signup request containing verification proof, password, birth date, and required consents. |
| UR-02 | Reject a missing, malformed, expired, or already-consumed proof with one safe public error. |
| UR-03 | Accept only users who satisfy the age-19 product rule. |
| UR-04 | Require the three current consent types once each. |
| UR-05 | Require a password with at least 8 characters and at most 72 UTF-8 bytes. |
| UR-06 | Encode the password through Spring Security `PasswordEncoder`; never store or log plaintext. |
| UR-07 | Enforce canonical-email uniqueness in MySQL and return a safe duplicate-email conflict. |
| UR-08 | In one service transaction, create user, credential, and consent records and delete the proof. |
| UR-09 | Roll back every registration write and keep the proof usable when persistence fails. |
| UR-10 | Return `201 SIGNUP_COMPLETED` without auto-login or token issuance. |
| UR-11 | Document the implemented endpoint and public errors in Korean Swagger. |

## 5. Data Boundary

Keep the existing minimal schema:

- `users`: identity, display email, canonical email, birth date, created time
- `local_credentials`: one BCrypt value per user
- `user_consents`: required consent type, version, and agreed time
- `email_verification_proofs`: handoff record owned by `email-verification` and consumed by signup

Do not add account lifecycle, profile, audit-actor, soft-delete, session, or token columns for registration.

## 6. Transaction Learning Goal

The central learning artifact is one visible `@Transactional` signup boundary:

1. Resolve and lock the proof.
2. Validate the request.
3. Check email uniqueness.
4. Encode and store the password safely.
5. Save the account and consent records.
6. Delete the proof.
7. Commit everything together or roll everything back.

The implementation may use one service or a small helper, but it must not split into extra abstractions merely to optimize lock duration or model production-scale contention.

## 7. Verification Boundary

Required representative evidence:

- Password, age, and consent happy/boundary validation
- Web success plus representative `400`, `409`, and `422` responses
- Actual-MySQL successful signup
- Actual-MySQL rollback with no partial account data
- Consumed proof cannot be reused
- Canonical email remains unique
- Swagger schema hides password and proof examples and documents the public contract

Not required for completion:

- every validation-order permutation
- malformed UTF-16 surrogate tests
- proof-replacement tests
- same-proof thread orchestration
- exact named-constraint exception-chain tests
- separate tests for framework defaults already covered by the core flow

Existing tests may be kept when useful. Tests made obsolete by this approved behavior change are updated or removed together with the owning logic; they are not deleted merely to hide a failure.

## 8. Swagger and Deployment Boundary

- OpenAPI remains disabled by default.
- The developer may explicitly enable Swagger UI and Try it out in local development or a protected small deployment used by the developer and invited acquaintances.
- Password and verification proof are `writeOnly` and have no examples or defaults.
- Swagger descriptions remain detailed Korean explanations of the implemented request, response, validation, and application errors.
- Public or unrestricted Swagger exposure is not part of this toy project.

## 9. Completion Criteria

- [ ] Implementation matches `USER-REGISTRATION-009` rather than the superseded operation-scale clauses.
- [ ] The large password blocklist and unused registration-owned email policy are removed.
- [ ] The signup transaction remains clear and rollback-safe.
- [ ] Representative tests pass, including the required actual-MySQL transaction checks.
- [ ] Swagger Try it out can be explicitly enabled for direct testing.
- [ ] The Gap Analysis is regenerated against this Plan and Design.
- [ ] Report is written only after the simplified implementation is verified.

## 10. `USER-REGISTRATION-009` Scope Reduction

**Approved:** 2026-08-14 <br>
**Reason:** The earlier registration contract accumulated production-oriented password screening, pre-transaction optimization, concurrency orchestration, exact exception classification, and exhaustive testing disproportionate to the personally used toy project.

This decision:

- removes the Top-100k/local compromised-password blocklist requirement
- replaces the earlier 15-code-point, NFC, malformed-surrogate contract with a basic 8-character and 72-UTF-8-byte boundary
- retains BCrypt, secret protection, canonical-email uniqueness, proof locking, atomic persistence, and rollback
- removes concurrency, proof replacement, and exact constraint-name classification as completion gates
- permits a straightforward service transaction even if it is not optimized for commercial traffic
- allows Swagger Try it out in an explicitly enabled safe environment
- keeps only representative tests needed to explain the behavior
- defers account deletion until after the core toy-project flow works

## 11. Decision History

| Decision | Current status |
|----------|----------------|
| `USER-REGISTRATION-001-R1` | Retained only for verification-before-account creation |
| `AUTH-001-R2` | Retained; no account-status lifecycle |
| `AUTH-003` | Password complexity portions superseded by `USER-REGISTRATION-009`; BCrypt byte safety retained |
| `AUTH-004` | Superseded by `USER-REGISTRATION-009`; no password blocklist in the toy-project target |
| `AUTH-005` | Retained only for proof-before-signup and atomic account creation |
| `FOUNDATION-005` | Deferred by `FOUNDATION-006` |
| `USER-REGISTRATION-002` | Retained; minimal account, credential, and consent schema |
| `USER-REGISTRATION-003` | Retained; final signup API and safe response boundary |
| `USER-REGISTRATION-004` | Retained only for hashed, expiring, one-time proof and transactional consumption |
| `USER-REGISTRATION-005` | Retained; `PasswordEncoder` and BCrypt storage |
| `USER-REGISTRATION-006` | Exhaustive verification clauses superseded by representative tests |
| `USER-REGISTRATION-007` | Retained except Try it out prohibition, which is superseded |
| `USER-REGISTRATION-008` | Superseded as a completion requirement |
| `USER-REGISTRATION-009` | Approved current scope |

## Version History

| Version | Date | Change |
|---------|------|--------|
| 2.0.2 | 2026-08-14 | Adopted `FOUNDATION-006-R1`: social signup/login remains in the project but is owned by a separate feature |
| 2.0.1 | 2026-08-14 | Clarified that a few acquaintances may register for voluntary trial and feedback without changing the non-commercial toy-project boundary |
| 2.0.0 | 2026-08-14 | Replaced the limited-service registration Plan with the personally deployed toy-project scope in `USER-REGISTRATION-009` |
| 1.2.7 | 2026-08-14 | Historical Plan before the toy-project scope reset |
