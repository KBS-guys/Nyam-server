# email-verification - Plan Document

> **Summary**: Provide the minimum Mailpit email-verification flow that issues a one-time verificationProof consumed by the existing signup API. <br>
> **Project**: Nyamlog <br>
> **Version**: 1.1.0 <br>
> **Author**: Project decision record <br>
> **Date**: 2026-08-21 <br>
> **Status**: Approved <br>
> **Decision**: EMAIL-VERIFICATION-001 <br>
> **Review Process Decision**: EMAIL-VERIFICATION-006 <br>
> **Related Issue**: [#8](https://github.com/KBS-guys/Nyam-server/issues/8) <br>
> **Scope Authority**: FOUNDATION-006-R1, USER-REGISTRATION-009

---

## 1. Overview

### 1.1 Purpose

Send a signup verification code to Mailpit and issue a one-time verificationProof after the user confirms the correct code.

This feature owns email possession verification only. The completed user-registration feature owns account creation and proof consumption. The separate local-login feature owns login, Access Token issuance, and logout.

### 1.2 Background

The completed user-registration tests currently seed a valid proof directly before exercising signup. A person cannot yet start the complete flow from email delivery. This feature fills that boundary without expanding into login or production mail.

The existing email_verification_proofs table stores only a proof digest, display and canonical email, and creation and expiry times. VerificationProofHasher defines the existing 43-character URL-safe proof and SHA-256 lookup contract. Design must evaluate and preserve this contract unless an explicit contradiction is found.

### 1.3 Related Documents

- Issue: [#8 Mailpit email verification and signup proof issuance](https://github.com/KBS-guys/Nyam-server/issues/8)
- Foundation Plan: docs/01-plan/features/nyamlog-mvp-foundation.plan.md
- Foundation Design: docs/02-design/features/nyamlog-mvp-foundation.design.md
- User Registration Plan: docs/01-plan/features/user-registration.plan.md
- User Registration Design: docs/02-design/features/user-registration.design.md
- User Registration Analysis: docs/03-analysis/user-registration.analysis.md
- User Registration Report: docs/04-report/user-registration.report.md
- PDCA status: docs/.pdca-status.json

## 2. User Flow

1. The user enters an email and requests a verification code.
2. The server validates and canonicalizes the email.
3. The same send request checks whether the canonical email is already registered.
4. An already registered email receives no challenge and no email.
5. An available email receives a short-lived code through Mailpit.
6. The user submits the email and code for confirmation.
7. Confirmation checks the challenge, expiry, and attempt state, but does not repeat the registered-email lookup.
8. Successful confirmation invalidates the code and issues a one-time verificationProof.
9. The user sends the proof with password, birth date, and consents to the existing signup API.
10. Signup and the MySQL canonical-email UNIQUE constraint remain the final duplicate-account guard.

## 3. Scope

### 3.1 In Scope

- Signup-email validation and canonical identity
- Registered-email lookup inside the code-send request
- No challenge creation or mail delivery for an already registered email
- Verification-code delivery through local Mailpit
- Short code expiry
- Code confirmation and successful-code replay prevention
- Simple resend delay and resend-count limit
- Simple failed-attempt limit
- Issuance of the existing 43-character URL-safe verificationProof contract
- Evaluation and reuse of email_verification_proofs and VerificationProofHasher
- Handoff to the existing signup proof-consumption flow
- Detailed Korean Swagger documentation
- Manual Mailpit-to-Swagger happy-path verification
- Representative unit, web, and actual MySQL 8.4.5 tests

### 3.2 Out of Scope

- A separate email-availability endpoint or button
- Repeating the registered-email lookup during code confirmation
- local-login, Access Token issuance, and logout
- social-login
- Password reset and account recovery
- Production mail infrastructure or credentials
- Redis, distributed rate limiting, or multi-instance coordination
- Advanced cleanup jobs or long-term verification history
- Commercial security monitoring or large-scale abuse prevention
- account-deletion

## 4. Functional Requirements

| ID | Requirement |
|----|-------------|
| EV-01 | The send request validates the email and derives its canonical identity. |
| EV-02 | The send request checks whether the canonical email is already registered. |
| EV-03 | An already registered email creates no challenge and sends no mail. |
| EV-04 | An available email receives a short-lived verification code through Mailpit. |
| EV-05 | The raw verification code is not persistently stored or logged. |
| EV-06 | Confirmation validates the code, expiry, and failed-attempt state. |
| EV-07 | Confirmation does not repeat the registered-email lookup. |
| EV-08 | A successfully confirmed code cannot be reused. |
| EV-09 | Resend invalidates the previous code and applies a simple resend limit. |
| EV-10 | Successful confirmation issues a one-time verificationProof compatible with signup. |
| EV-11 | The raw proof is returned once and is not persisted or logged. |
| EV-12 | Existing signup behavior and the MySQL UNIQUE constraint remain the final duplicate-account guard. |
| EV-13 | Swagger supports the send, confirm, proof, and signup flow when explicitly enabled. |

## 5. Non-Functional Requirements

### 5.1 Security

- Do not log verification codes, raw proofs, mail credentials, or real personal information.
- Make verification codes and proofs resistant to guessing and replay within the approved toy-project boundary.
- Do not expose database errors or internal exceptions to clients.
- Do not add verification-code or proof examples or defaults to Swagger.

### 5.2 Data Integrity

- Successful code consumption and proof issuance must not leave a partial intermediate state.
- A resend must make the previous code unusable.
- A successful signup must make its proof unusable.
- Verify MySQL-specific constraints, locking, and rollback against actual MySQL 8.4.5.

### 5.3 Simplicity

- Add only the Controller, Service, Repository, Entity, configuration, and tests required by the approved flow.
- Do not add Redis before observed traffic or multi-instance requirements justify it.
- Do not introduce production mail or commercial abuse-control infrastructure.

## 6. Success Criteria

- [ ] The send, confirm, and proof-issuance APIs match the approved Design.
- [ ] An already registered email receives no verification mail.
- [ ] A confirmed code cannot be reused.
- [ ] A resend invalidates the previous code.
- [ ] The issued proof can be consumed by signup only once.
- [ ] The complete happy path works through Mailpit and Swagger.
- [ ] Swagger and logs do not expose sensitive values.
- [ ] Applicable MySQL integration tests execute without skip against MySQL 8.4.5.
- [ ] .\gradlew.bat test javadoc succeeds.
- [ ] git diff --check succeeds.

## 7. Risks and Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Failure between code consumption and proof issuance leaves partial state | High | Define one clear transactional outcome in Design and verify rollback on MySQL. |
| Resend leaves an older code valid | High | Define replacement semantics and test rejection of the previous code. |
| Codes or proofs appear in logs or Swagger | High | Enforce no-raw-secret persistence or logging and extend focused contract tests. |
| Another signup completes between the send check and final signup | Medium | Treat the send check only as mail suppression and retain signup plus the MySQL UNIQUE constraint as final authority. |
| Mail delivery failure and challenge state disagree | Medium | Decide persistence and delivery ordering with one explicit failure contract. |
| Scope expands into production mail or distributed abuse control | Medium | Keep Mailpit, one application instance, and simple limits as the approved boundary. |

## 8. Consolidated Design Review Scope

Review the feature as one complete Design draft rather than approving each parameter or implementation detail separately. Organize the remaining concerns into three functional bundles:

1. Data and security model: authoritative challenge state, code protection, proof separation, database atomicity, and concurrency principles
2. Operating policy: code lifetime, resend and failed-attempt limits, reset boundaries, expiry handling, and existing-proof replacement
3. Request flow and failure contract: email canonicalization, public send and confirmation APIs, mail configuration and ordering, failure outcomes, database structure constraints, Swagger, and representative tests

EMAIL-VERIFICATION-002 through EMAIL-VERIFICATION-005 remain approved and are not reopened by this review structure. The consolidated draft must preserve their security, integrity, lifetime, and transaction requirements.

Design must approve public request and response field meanings, validation rules, public error categories, database constraints, transaction outcomes, concurrency outcomes, and representative verification scenarios. Exact class names, repository method names, exception-class structure, SQL statement text, test method names, and internal package paths may be selected during implementation when they do not change the approved contract.

Create a separate decision only when a newly discovered issue materially changes schema, public API, security, ownership, transaction behavior, concurrency outcome, or feature scope.

## 9. Feature Handoff Boundary

- user-registration consumes the proof and atomically creates the account, credential, and consents.
- local-login owns login, Access Token issuance, protected API authentication, and logout.
- social-login owns provider authentication and first-login account creation.
- Production mail and account-deletion remain separate future scopes.

## 10. Approval Record

### EMAIL-VERIFICATION-001 - Approved 2026-08-21

Approved the Issue #8 boundary for Mailpit delivery, code confirmation, simple resend and attempt limits, one-time proof issuance, existing signup integration, Korean Swagger documentation, and actual MySQL verification.

This approval does not authorize:

- Design start or completion
- Selection of challenge storage
- Exact code, expiry, resend, or failed-attempt values
- Concrete API, error, Mailpit, or Migration design
- Code, dependency, configuration, Migration, or test implementation
- local-login, social-login, production mail, or Archive

### EMAIL-VERIFICATION-006 - Approved 2026-08-21

Supersedes only the sequential, parameter-by-parameter Design review process in the original Section 8 and Section 11. Review all remaining Design concerns in one consolidated draft, preserve EMAIL-VERIFICATION-002 through EMAIL-VERIFICATION-005, and require one explicit whole-Design approval before implementation authorization.

This approval does not complete Design, approve any unresolved functional value or public contract, authorize implementation, or change the feature boundary.

## 11. Next Step

Prepare one consolidated draft that resolves all remaining Design concerns and presents the complete feature flow for review. After corrections, request one explicit whole-Design approval. Do not begin implementation until Design is complete and implementation is separately authorized.

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0.0 | 2026-08-21 | Approved the minimal Plan through EMAIL-VERIFICATION-001 | Project decision record |
| 1.1.0 | 2026-08-21 | Replaced sequential parameter approval with the EMAIL-VERIFICATION-006 consolidated Design review process | Project decision record |
